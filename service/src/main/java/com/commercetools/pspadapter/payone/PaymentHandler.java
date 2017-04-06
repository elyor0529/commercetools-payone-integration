package com.commercetools.pspadapter.payone;

import com.commercetools.pspadapter.payone.domain.ctp.CommercetoolsQueryExecutor;
import com.commercetools.pspadapter.payone.domain.ctp.PaymentWithCartLike;
import com.commercetools.pspadapter.payone.domain.ctp.exceptions.NoCartLikeFoundException;
import io.sphere.sdk.client.ErrorResponseException;
import io.sphere.sdk.client.NotFoundException;
import io.sphere.sdk.http.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletionException;

import static io.sphere.sdk.http.HttpStatusCode.INTERNAL_SERVER_ERROR_500;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

public class PaymentHandler {

    /**
     * How many times to retry if {@link ConcurrentModificationException} happens.
     */
    private static final int RETRIES_LIMIT = 20;
    private static final int RETRY_DELAY = 100; // msec

    private final String payoneInterfaceName;

    private final CommercetoolsQueryExecutor commercetoolsQueryExecutor;
    private final PaymentDispatcher paymentDispatcher;

    private final Logger logger;

    public PaymentHandler(String payoneInterfaceName, String tenantName,
                          CommercetoolsQueryExecutor commercetoolsQueryExecutor, PaymentDispatcher paymentDispatcher) {
        this.payoneInterfaceName = payoneInterfaceName;

        this.commercetoolsQueryExecutor = commercetoolsQueryExecutor;
        this.paymentDispatcher = paymentDispatcher;

        this.logger = LoggerFactory.getLogger(format("%s.%s", this.getClass().getName(), tenantName));
    }

    /**
     * Tries to handle the payment with the provided ID.
     *
     * @param paymentId identifies the payment to be processed
     * @return the result of handling the payment
     */
    public PaymentHandleResult handlePayment(@Nonnull final String paymentId) {
        try {
            for (int i = 0; i < RETRIES_LIMIT; i++) {
                try {
                    final PaymentWithCartLike payment = commercetoolsQueryExecutor.getPaymentWithCartLike(paymentId);
                    String paymentInterface = payment.getPayment().getPaymentMethodInfo().getPaymentInterface();
                    if (!payoneInterfaceName.equals(paymentInterface)) {
                        logger.warn("Wrong payment interface name: expected [{}], found [{}]", payoneInterfaceName, paymentInterface);
                        return new PaymentHandleResult(HttpStatusCode.BAD_REQUEST_400);
                    }

                    final PaymentWithCartLike result = paymentDispatcher.dispatchPayment(payment);
                    return new PaymentHandleResult(HttpStatusCode.OK_200);
                } catch (final ConcurrentModificationException cme) {
                    Thread.sleep(RETRY_DELAY);
                }
            }

            return new PaymentHandleResult(HttpStatusCode.ACCEPTED_202);

        } catch (final NotFoundException | NoCartLikeFoundException e) {
            return handleNotFoundException(paymentId, e);
        } catch (final ErrorResponseException e) {
            return errorResponseHandler(e);
        } catch (final CompletionException e) {
            return completionExceptionHandler(e);
        } catch (final Exception e) {
            return logThrowableInResponse(e);
        }
    }

    private PaymentHandleResult handleNotFoundException(@Nonnull String paymentId, RuntimeException e) {
        final String body = format("Could not process payment with ID [%s], cause: %s", paymentId, e.getMessage());
        return new PaymentHandleResult(HttpStatusCode.NOT_FOUND_404, body);
    }

    private PaymentHandleResult errorResponseHandler(ErrorResponseException e) {
        logger.warn("An Error Response from commercetools platform", e);
        return new PaymentHandleResult(e.getStatusCode(), e.getMessage());
    }

    /**
     * Completion exceptions used to tell nothing, thus we should try to report the exception cause.
     * @param e Exception to report
     * @return {@link PaymentHandleResult} with 500 status and message which refers to the logs.
     */
    private PaymentHandleResult completionExceptionHandler(CompletionException e) {
        String causeMessage = ofNullable(e.getCause()).map(Throwable::toString).orElse("null");
        logger.error("Completion exception error: {}\nCause: {}", e.toString(), causeMessage);
        return new PaymentHandleResult(INTERNAL_SERVER_ERROR_500,
                "An error occurred during communication with the commercetools platform, see the service logs");
    }

    private PaymentHandleResult logThrowableInResponse(Throwable throwable) {
        logger.error("Error in response: ", throwable);
        return new PaymentHandleResult(INTERNAL_SERVER_ERROR_500, "Unexpected error. See the service logs");
    }
}
