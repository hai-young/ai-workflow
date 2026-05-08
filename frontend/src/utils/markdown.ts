import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight: function (str: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' + hljs.highlight(str, { language: lang, ignoreIllegals: true }).value + '</code></pre>'
      } catch { /* fall through */ }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

// Custom renderer to make citation references [1] clickable
const defaultLinkRender = md.renderer.rules.link_open || function(tokens, idx, options, _env, self) {
  return self.renderToken(tokens, idx, options)
}

// Keep [N] as clickable citation references
md.renderer.rules.link_open = function(tokens, idx, options, env, self) {
  const token = tokens[idx]
  const href = token.attrGet('href')
  if (href === '@ref') {
    token.attrSet('class', 'citation-ref')
    return '<span class="citation-ref">'
  }
  return defaultLinkRender(tokens, idx, options, env, self)
}

md.renderer.rules.link_close = function(tokens, idx, _options, _env, _self) {
  const token = tokens[idx - 2] // look back to open token
  if (token && token.attrGet('class') === 'citation-ref') {
    return '</span>'
  }
  return '</a>'
}

export function renderMarkdown(text: string): string {
  return md.render(text)
}
