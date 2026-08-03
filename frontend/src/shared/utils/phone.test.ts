import { describe, it, expect } from 'vitest';
import { formatPhoneNumber } from './phone';

describe('formatPhoneNumber', () => {
  it('숫자가 3자리 이하면 그대로 반환한다', () => {
    expect(formatPhoneNumber('010')).toBe('010');
  });

  it('4~7자리면 3-n 형식으로 하이픈을 넣는다', () => {
    expect(formatPhoneNumber('01012')).toBe('010-12');
    expect(formatPhoneNumber('0101234')).toBe('010-1234');
  });

  it('8자리 이상이면 3-4-4 형식으로 하이픈을 넣는다', () => {
    expect(formatPhoneNumber('01012345678')).toBe('010-1234-5678');
  });

  it('숫자가 아닌 문자는 제거한다', () => {
    expect(formatPhoneNumber('010-abc1234-5678')).toBe('010-1234-5678');
  });

  it('11자리를 초과하는 입력은 잘라낸다', () => {
    expect(formatPhoneNumber('010123456789999')).toBe('010-1234-5678');
  });
});