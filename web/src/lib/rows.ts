/** "A-C" or "A, B, AA" -> ["A","B","C"] / ["A","B","AA"]. Ranges work for single letters. */
export function parseRows(input: string): string[] {
  const rows: string[] = []
  for (const part of input.toUpperCase().split(',').map((p) => p.trim()).filter(Boolean)) {
    const range = /^([A-Z])\s*-\s*([A-Z])$/.exec(part)
    if (range) {
      for (let c = range[1].charCodeAt(0); c <= range[2].charCodeAt(0); c++) rows.push(String.fromCharCode(c))
    } else {
      rows.push(part)
    }
  }
  return rows
}
