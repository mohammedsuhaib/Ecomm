package com.townbasket.catalog.internal;

import java.util.Optional;

/**
 * Port for transliterating an English product name into another script
 * (currently Kannada). Implementations are an internal catalog detail; the
 * result is stored on the product (see {@code name_kn}) so it is computed at most
 * once per product and never on the read path.
 */
interface ProductNameTransliterator {

    /** Returns the transliterated name, or empty if it could not be produced. */
    Optional<String> toKannada(String englishName);
}
