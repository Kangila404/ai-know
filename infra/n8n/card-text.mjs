// Embedded in the n8n import node; only whitespace changes, never editorial wording.
export function formatCardText(value) {
  const segmenter = new Intl.Segmenter('ko', {granularity:'sentence'});
  return value.replace(/\r\n?/g, '\n').split(/(\n+)/).map(line => {
    if (!line || line.startsWith('\n')) return line;
    const parts=[];
    let prefix='';
    for (const {segment} of segmenter.segment(line)) {
      // Keep numbered/bulleted list markers with their sentence.
      if (/^\s*(?:\d+|[A-Za-z])[.]\s*$/.test(segment)) {prefix+=segment;continue;}
      parts.push((prefix+segment).trim());prefix='';
    }
    if(prefix)parts.push(prefix.trimEnd());
    return parts.join('\n');
  }).join('');
}
