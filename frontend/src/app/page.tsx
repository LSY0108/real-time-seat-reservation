import Link from 'next/link';
import { Header } from '@/components/layout/Header';

export default function Home() {
  return (
    <main className="relative flex min-h-screen flex-col items-center justify-center px-4">
      <div className="absolute right-4 top-4">
        <Header />
      </div>

      <span className="mb-4 text-4xl" aria-hidden>
        ⚾
      </span>
      <h1 className="mb-2 text-3xl font-bold tracking-tight text-foreground">좌석 예매</h1>
      <p className="mb-10 text-sm text-zinc-400">공연을 선택해 좌석을 예매하세요.</p>
      <div className="flex flex-wrap justify-center gap-3">
        <Link
          href="/shows/1/seats"
          className="rounded-md border-2 border-dashed border-zinc-900/20 bg-stadium-gold px-6 py-3 text-sm font-bold text-zinc-900 shadow-[0_0_20px_rgba(246,201,69,0.35)] transition-colors hover:bg-stadium-gold-strong"
        >
          공연 입장
        </Link>
        <Link
          href="/my/reservations"
          className="rounded-md border border-white/15 px-6 py-3 text-sm font-medium text-zinc-300 transition-colors hover:bg-white/5"
        >
          내 예약 보기
        </Link>
      </div>
    </main>
  );
}
