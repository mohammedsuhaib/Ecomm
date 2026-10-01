import Link from 'next/link';
import { useLocale } from 'next-intl';
import { categoryDisplayName } from '@/app/lib/productName';
import type { Category } from '@/app/lib/types';

/** Category tile linking to the category browse view. */
export default function CategoryCard({ category }: { category: Category }) {
  const locale = useLocale();
  return (
    <Link href={`/category/${category.slug}`} className="category-card">
      {category.imageUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={category.imageUrl} alt="" loading="lazy" />
      ) : (
        <span className="cat-emoji" aria-hidden>
          🧺
        </span>
      )}
      <span>{categoryDisplayName(category, locale)}</span>
    </Link>
  );
}
