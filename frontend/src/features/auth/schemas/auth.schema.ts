import { z } from 'zod';

export const loginSchema = z.object({
  email: z
    .string()
    .min(1, '이메일은 필수입니다.')
    .email('올바른 이메일 형식이어야 합니다.'),
  password: z.string().min(1, '비밀번호는 필수입니다.'),
});

export const signupSchema = z.object({
  email: z
    .string()
    .min(1, '이메일은 필수입니다.')
    .email('올바른 이메일 형식이어야 합니다.'),
  password: z.string().min(8, '비밀번호는 8자 이상이어야 합니다.'),
  name: z.string().min(1, '이름은 필수입니다.'),
  phone: z
    .string()
    .min(1, '전화번호는 필수입니다.')
    .regex(/^\d{2,3}-\d{3,4}-\d{4}$/, '올바른 전화번호 형식이 아닙니다.'),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
export type SignupFormValues = z.infer<typeof signupSchema>;