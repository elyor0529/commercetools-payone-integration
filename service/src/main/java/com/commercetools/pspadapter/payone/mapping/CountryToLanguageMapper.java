package com.commercetools.pspadapter.payone.mapping;

import com.neovisionaries.i18n.CountryCode;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Optional;

/**
 * Map country code to language. For now only one-to-one implementation is expected because this is Payone/Klarna
 * specific requirements. This might be extended in future with other methods, like country to locales list.
 */
public interface CountryToLanguageMapper {

    /**
     * Optionally (if possible) map {@code country} to respective {@link Locale} with language.
     * @param country {@link CountryCode} which try to convert to a language.
     * @return
     */
    Optional<Locale> mapCountryToLanguage(@Nullable CountryCode country);
}
