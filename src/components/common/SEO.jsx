import { useEffect } from 'react';

/**
 * Head and SEO manager component for public and protected routes (Phase 20).
 * Handles dynamic title, meta descriptions, canonical URLs, robots crawl directives,
 * Open Graph social tags, and Schema.org structured data.
 */
export default function SEO({
  title = 'MedTrack – AVS | Hospital Asset Tracking & Geolocation Dashboard',
  description = 'Real-time hospital asset tracking system and mobile medical equipment geolocation dashboard utilizing QR identification, physical GPS telemetry, and live geofencing.',
  canonicalPath = '',
  noindex = false,
  structuredData = null,
  ogType = 'website',
  ogImage = '/medtrack-logo.svg',
}) {
  useEffect(() => {
    // 1. Page Title
    document.title = title;

    // Helper to get or create a <meta> element
    const setMetaTag = (attributeName, attributeValue, content) => {
      let meta = document.querySelector(`meta[${attributeName}="${attributeValue}"]`);
      if (!meta) {
        meta = document.createElement('meta');
        meta.setAttribute(attributeName, attributeValue);
        document.head.appendChild(meta);
      }
      meta.setAttribute('content', content);
    };

    // 2. Meta Description
    setMetaTag('name', 'description', description);

    // 3. Robots Crawl Directives
    const robotsContent = noindex ? 'noindex, nofollow' : 'index, follow';
    setMetaTag('name', 'robots', robotsContent);

    // 4. Configurable Canonical URL
    const siteOrigin =
      (typeof import.meta !== 'undefined' && import.meta.env?.VITE_SITE_URL) ||
      (typeof window !== 'undefined' ? window.location.origin : '');

    let canonicalLink = document.querySelector('link[rel="canonical"]');
    if (canonicalPath) {
      if (!canonicalLink) {
        canonicalLink = document.createElement('link');
        canonicalLink.setAttribute('rel', 'canonical');
        document.head.appendChild(canonicalLink);
      }
      canonicalLink.setAttribute('href', `${siteOrigin}${canonicalPath}`);
    } else if (canonicalLink && noindex) {
      // Remove canonical on private noindex pages
      canonicalLink.remove();
    }

    // 5. Open Graph Meta Tags (Only on public/indexable pages)
    if (!noindex) {
      const fullUrl = `${siteOrigin}${canonicalPath || ''}`;
      const fullImageUrl = ogImage.startsWith('http') ? ogImage : `${siteOrigin}${ogImage}`;

      setMetaTag('property', 'og:title', title);
      setMetaTag('property', 'og:description', description);
      setMetaTag('property', 'og:type', ogType);
      setMetaTag('property', 'og:url', fullUrl);
      setMetaTag('property', 'og:image', fullImageUrl);
    }

    // 6. Schema.org JSON-LD Structured Data
    let scriptTag = document.querySelector('script#schema-structured-data');
    if (structuredData && !noindex) {
      if (!scriptTag) {
        scriptTag = document.createElement('script');
        scriptTag.id = 'schema-structured-data';
        scriptTag.type = 'application/ld+json';
        document.head.appendChild(scriptTag);
      }
      scriptTag.textContent = JSON.stringify(structuredData);
    } else if (scriptTag) {
      scriptTag.remove();
    }

    return () => {
      // Optional cleanup on unmount
    };
  }, [title, description, canonicalPath, noindex, structuredData, ogType, ogImage]);

  return null;
}
