import Link from 'next/link';

export function BackHomeButton() {
  return (
    <Link
      href="/"
      className="mb-4 inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-stadium-gold"
    >
      <span aria-hidden>←</span> 홈으로
    </Link>
  );
}