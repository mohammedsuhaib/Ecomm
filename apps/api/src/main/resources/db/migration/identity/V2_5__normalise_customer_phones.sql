-- Canonicalise stored customer phones to the bare 10-digit national form the
-- rest of the app assumes (order placement validates [0-9]{10}, every UI adds
-- the +91 prefix itself).
--
-- Rows in E.164 shape (+919632500797) were written by deployments running the
-- real Firebase verifier before IndianPhone.normalise() existed; the offline
-- verifier wrote bare digits for the same human. Left as they are, such a
-- customer has two accounts and the E.164 one cannot place an order.
--
-- phone is UNIQUE, so a row is only rewritten when its normalised form is FREE.
-- Where both shapes exist for one person the rows are left untouched and named
-- below: merging them means moving orders between user ids, which is a decision
-- for a human, not a migration.

DO $$
DECLARE
    r        RECORD;
    target   TEXT;
    conflicts INT := 0;
    fixed     INT := 0;
BEGIN
    FOR r IN
        SELECT id, phone FROM identity.users
         WHERE phone IS NOT NULL
           AND phone !~ '^[0-9]{10}$'
    LOOP
        target := regexp_replace(r.phone, '[^0-9]', '', 'g');

        -- Same length rules as IndianPhone.normalise: a bare 10-digit mobile can
        -- itself begin "91", so only a 12-digit value may lose that prefix.
        IF length(target) = 12 AND left(target, 2) = '91' THEN
            target := substr(target, 3);
        ELSIF length(target) = 11 AND left(target, 1) = '0' THEN
            target := substr(target, 2);
        END IF;

        IF target !~ '^[0-9]{10}$' THEN
            RAISE NOTICE 'identity.users id=% phone=% is not an Indian mobile; left as is',
                r.id, r.phone;
            conflicts := conflicts + 1;
            CONTINUE;
        END IF;

        IF EXISTS (SELECT 1 FROM identity.users WHERE phone = target AND id <> r.id) THEN
            RAISE NOTICE 'identity.users id=% phone=% already exists as % on another row; '
                         'left as is — merge the two accounts by hand if they are one person',
                r.id, r.phone, target;
            conflicts := conflicts + 1;
            CONTINUE;
        END IF;

        UPDATE identity.users SET phone = target, updated_at = now() WHERE id = r.id;
        fixed := fixed + 1;
    END LOOP;

    RAISE NOTICE 'phone normalisation: % rewritten, % left for manual review', fixed, conflicts;
END $$;
