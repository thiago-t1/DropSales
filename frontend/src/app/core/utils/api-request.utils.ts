export function isApiRequest(requestUrl: string, apiBaseUrl: string): boolean {
  const baseUrl = apiBaseUrl.replace(/\/+$/, '');
  return baseUrl.length > 0
    && (requestUrl === baseUrl || requestUrl.startsWith(`${baseUrl}/`));
}
