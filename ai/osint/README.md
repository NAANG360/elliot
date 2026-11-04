# Elliot OSINT subsystem

OSINT is public-source research, not unrestricted surveillance. The subsystem collects only explicitly configured public feeds and URLs, stores source metadata and content hashes, and produces cited research notes. It must not attempt authentication bypass, private-account access, doxxing, credential collection, or targeting individuals.

## Pipeline

Discover -> fetch -> normalize -> deduplicate -> provenance record -> corroborate -> summarize -> archive -> brief.

Every item stores: canonical URL, publisher, title, published timestamp, retrieval timestamp, content hash, source type, licence/terms note, and confidence. Distinguish source claims from independently corroborated facts. Preserve contradictory reports rather than silently selecting one.

## Initial public feeds

Feeds are configured in `feeds.json`. Start with official release/security/research feeds; avoid social-media scraping. Respect robots/terms, rate limits, caching and publisher restrictions. RSS snippets are leads, not sufficient evidence for high-impact claims.

## Briefing

The daily brief groups AI, software, cybersecurity, science and world affairs. Each entry links to its original source, marks its age, and labels confidence. High-impact claims require multiple independent sources or an explicit "single-source / unverified" label.

## Privacy

No people-search or personal profiling features. Keep local archives on-device; do not upload the user's queries or memory to feed publishers. Feed retrieval is opt-in and network use should be visible.
