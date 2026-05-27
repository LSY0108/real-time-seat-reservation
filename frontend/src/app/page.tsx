import Link from 'next/link';

export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center bg-zinc-50 px-4">
      <h1 className="mb-2 text-2xl font-semibold text-zinc-900">좌석 예매</h1>
      <p className="mb-8 text-sm text-zinc-500">공연을 선택해 좌석을 예매하세요.</p>
      <Link
        href="/shows/1/seats"
        className="rounded-md bg-zinc-900 px-6 py-3 text-sm font-medium text-white transition-colors hover:bg-zinc-700"
      >
        공연 입장
      </Link>
    </main>
  );
}
