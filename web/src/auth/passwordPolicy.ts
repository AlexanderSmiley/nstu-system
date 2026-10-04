/**
 * Client-side mirror of the server password policy (identity spec
 * "Обязательная смена временного пароля"). The server remains authoritative;
 * this only gives immediate feedback.
 *
 * @returns an error message, or `null` when the values are acceptable
 */
export function validateNewPassword(
  newPassword: string,
  oldPassword: string,
  confirmation: string,
): string | null {
  if (newPassword.length < 8) {
    return 'Пароль должен содержать не менее 8 символов'
  }
  if (!/\p{L}/u.test(newPassword) || !/\p{Nd}/u.test(newPassword)) {
    return 'Пароль должен содержать и буквы, и цифры'
  }
  if (newPassword === oldPassword) {
    return 'Новый пароль должен отличаться от текущего'
  }
  if (newPassword !== confirmation) {
    return 'Пароли не совпадают'
  }
  return null
}
