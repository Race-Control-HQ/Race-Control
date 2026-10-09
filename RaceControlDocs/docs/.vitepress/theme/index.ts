// docs/.vitepress/theme/index.ts
import DefaultTheme from 'vitepress/theme'
import "./style.css";
import "./custom.css";

export default {
  extends: DefaultTheme,
  enhanceApp() {
    // Click a screenshot to open it full size in a new tab.
    if (typeof window === 'undefined') return
    window.addEventListener('click', (event) => {
      const img = event.target as HTMLElement | null
      if (img instanceof HTMLImageElement && img.closest('.vp-doc') && img.src) {
        window.open(img.src, '_blank', 'noopener')
      }
    })
  },
}
