package com.commercetools.pspadapter.payone.notification.common;

import com.commercetools.payments.TransactionStateResolver;
import com.commercetools.pspadapter.payone.domain.payone.model.common.ClearingType;
import com.commercetools.pspadapter.payone.domain.payone.model.common.Notification;
import com.commercetools.pspadapter.payone.domain.payone.model.common.NotificationAction;
import com.commercetools.pspadapter.payone.notification.NotificationProcessorBase;
import com.commercetools.pspadapter.tenant.TenantConfig;
import com.commercetools.pspadapter.tenant.TenantFactory;
import com.google.common.collect.ImmutableList;
import io.sphere.sdk.commands.UpdateAction;
import io.sphere.sdk.payments.*;
import io.sphere.sdk.payments.commands.updateactions.AddTransaction;
import io.sphere.sdk.payments.commands.updateactions.ChangeTransactionState;
import io.sphere.sdk.utils.MoneyImpl;

import javax.annotation.Nonnull;
import javax.money.MonetaryAmount;
import java.util.List;

/**
 * A NotificationProcessor for notifications with txaction "capture".
 *
 * @author Jan Wolter
 */
public class CaptureNotificationProcessor extends NotificationProcessorBase {

    public CaptureNotificationProcessor(TenantFactory tenantFactory, TenantConfig tenantConfig,
                                        TransactionStateResolver transactionStateResolver) {
        super(tenantFactory, tenantConfig, transactionStateResolver);
    }

    @Override
    protected boolean canProcess(final Notification notification) {
        return NotificationAction.CAPTURE.equals(notification.getTxaction());
    }

    @Override
    protected ImmutableList<UpdateAction<Payment>> createPaymentUpdates(final Payment payment,
                                                                        final Notification notification) {
        final ImmutableList.Builder<UpdateAction<Payment>> actionsBuilder = ImmutableList.builder();
        actionsBuilder.addAll(super.createPaymentUpdates(payment, notification));

        final List<Transaction> transactions = payment.getTransactions();
        final String sequenceNumber = toSequenceNumber(notification.getSequencenumber());

        return findMatchingTransaction(transactions, TransactionType.CHARGE, sequenceNumber)
                .map(transaction -> updateChargeTransactionState(transaction, notification, actionsBuilder))
                .orElseGet(() -> createChargeTransaction(notification, actionsBuilder, sequenceNumber))
                .build();
    }

    /**
     * Add {@link ChangeTransactionState} action to {@code actionsBuilder} if current transaction is still in different
     * from state in {@code notification}.
     */
    protected ImmutableList.Builder<UpdateAction<Payment>> updateChargeTransactionState(
            @Nonnull final Transaction transaction,
            @Nonnull final Notification notification,
            @Nonnull final ImmutableList.Builder<UpdateAction<Payment>> actionsBuilder) {
        final TransactionState newTransactionState = notification.getTransactionStatus().getCtTransactionState();
        if (transaction.getState() != newTransactionState) {
            actionsBuilder.add(ChangeTransactionState.of(newTransactionState, transaction.getId()));
        }

        return actionsBuilder;
    }

    /**
     * For payments except {@code BANK_TRANSFER-ADVANCE} ({@link ClearingType#PAYONE_VOR}) add {@link AddTransaction}
     * update action with {@link TransactionType#CHARGE} and state equal to
     * {@link io.sphere.sdk.payments.TransactionState Notification#getTransactionStatus()#getCtTransactionState()}
     */
    protected ImmutableList.Builder<UpdateAction<Payment>> createChargeTransaction(
            @Nonnull final Notification notification,
            @Nonnull final ImmutableList.Builder<UpdateAction<Payment>> actionsBuilder,
            @Nonnull final String sequenceNumber) {
        final MonetaryAmount amount = MoneyImpl.of(notification.getPrice(), notification.getCurrency());

        //For all payment methods that use PayOne preauthorization and capture but only one CTP-charge
        //we must not create a second charge transaction
        //at the moment that is only "BANK_TRANSFER_ADVANCE"
        if (ClearingType.PAYONE_VOR != ClearingType.getClearingTypeByCode(notification.getClearingtype())) {
            actionsBuilder.add(AddTransaction.of(TransactionDraftBuilder.of(TransactionType.CHARGE, amount)
                    .timestamp(toZonedDateTime(notification))
                    .state(notification.getTransactionStatus().getCtTransactionState())
                    .interactionId(sequenceNumber)
                    .build()));
        }

        return actionsBuilder;
    }
}
