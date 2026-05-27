import { SeatsView } from '@/features/seat/components/SeatsView';

interface Props {
  params: Promise<{ showId: string }>;
}

export default async function SeatsPage({ params }: Props) {
  const { showId } = await params;
  return <SeatsView showId={Number(showId)} />;
}