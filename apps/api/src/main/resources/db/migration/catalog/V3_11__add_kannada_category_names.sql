-- Add Kannada name column to categories for transliteration support
ALTER TABLE catalog.categories ADD COLUMN name_kn VARCHAR(255);

-- Create index for future search support
CREATE INDEX idx_categories_name_kn ON catalog.categories(name_kn);
