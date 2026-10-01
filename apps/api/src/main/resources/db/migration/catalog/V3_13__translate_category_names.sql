-- Translated (not transliterated) Kannada names for the categories the shop
-- launched with: a category is a plain description ("Rice & Dals"), so it reads
-- better translated, whereas product names are mostly brands and stay phonetic.
--
-- Matched on slug AND the English name, so a category that has since been
-- renamed (QA's "beverages" is "Tea & Coffee") gets the translation of what it
-- is actually called, or is left for the backfill. Staff can change any of
-- these from the admin category form; a hand-entered name is never regenerated.
UPDATE catalog.categories c
   SET name_kn = t.name_kn
  FROM (VALUES
        ('atta-flours',        'atta & flours',        'ಆಟಾ ಮತ್ತು ಹಿಟ್ಟುಗಳು'),
        ('rice-dals',          'rice & dals',          'ಅಕ್ಕಿ ಮತ್ತು ಬೇಳೆಗಳು'),
        ('edible-oils-ghee',   'edible oils & ghee',   'ಅಡುಗೆ ಎಣ್ಣೆ ಮತ್ತು ತುಪ್ಪ'),
        ('spices-masalas',     'spices & masalas',     'ಸಾಂಬಾರ ಪದಾರ್ಥಗಳು ಮತ್ತು ಮಸಾಲೆಗಳು'),
        ('dairy',              'dairy',                'ಹಾಲಿನ ಉತ್ಪನ್ನಗಳು'),
        ('biscuits-snacks',    'biscuits & snacks',    'ಬಿಸ್ಕತ್ತುಗಳು ಮತ್ತು ತಿಂಡಿಗಳು'),
        ('beverages',          'beverages',            'ಪಾನೀಯಗಳು'),
        ('beverages',          'tea & coffee',         'ಚಹಾ ಮತ್ತು ಕಾಫಿ'),
        ('cleaning-household', 'cleaning & household', 'ಸ್ವಚ್ಛತೆ ಮತ್ತು ಗೃಹೋಪಯೋಗಿ ವಸ್ತುಗಳು'),
        ('personal-care',      'personal care',        'ವೈಯಕ್ತಿಕ ಆರೈಕೆ'),
        ('sauces-spreads',     'sauces & spreads',     'ಸಾಸ್‌ಗಳು ಮತ್ತು ಸ್ಪ್ರೆಡ್‌ಗಳು')
       ) AS t(slug, name, name_kn)
 WHERE c.slug = t.slug
   AND LOWER(TRIM(c.name)) = t.name;
