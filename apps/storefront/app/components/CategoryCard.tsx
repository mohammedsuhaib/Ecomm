import Link from 'next/link';
import type { Category } from '@/app/lib/types';

/** Category tile linking to the category browse view. */
export default function CategoryCard({ category }: { category: Category }) {
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
      <span>{category.name}</span>
    </Link>
  );
}
