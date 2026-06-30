import 'package:flutter/material.dart';
import 'package:flutter_custom_tabs/flutter_custom_tabs.dart';

import '../../core/design_system/design_system.dart';
import '../../shared/widgets/fc_segmented_tabs.dart';
import '../../shared/widgets/page_scaffold.dart';
import 'edu_content.dart';

/// The Financial Education Hub — ported from the web's
/// `components/financial-education-hub.tsx`.
///
/// A search box live-filters all video + reading content via [searchEducation];
/// a topic selector switches between the [eduTopics], showing that topic's
/// [videoLessons] and [readingResources] as tappable cards that open the source
/// URL in a custom tab.
class EducationScreen extends StatefulWidget {
  const EducationScreen({super.key});

  @override
  State<EducationScreen> createState() => _EducationScreenState();
}

class _EducationScreenState extends State<EducationScreen> {
  final TextEditingController _searchController = TextEditingController();
  String _query = '';
  int _topicIndex = 0;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _open(String url) async {
    try {
      await launchUrl(Uri.parse(url));
    } catch (_) {
      /* ignore */
    }
  }

  @override
  Widget build(BuildContext context) {
    final results = searchEducation(_query);
    final searching = _query.trim().isNotEmpty;
    final topic = eduTopics[_topicIndex];
    final videos = videoLessons[topic.id] ?? const [];
    final articles = readingResources[topic.id] ?? const [];

    return FCPageBody(
      title: 'Financial Education Hub',
      description: 'Your journey to financial confidence starts here.',
      children: [
        // Search
        FCTextField(
          controller: _searchController,
          hintText: 'Search videos and articles…',
          textInputAction: TextInputAction.search,
          prefix: const Icon(Icons.search),
          suffix: searching
              ? GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () {
                    _searchController.clear();
                    setState(() => _query = '');
                  },
                  child: const Icon(Icons.close),
                )
              : null,
          onChanged: (value) => setState(() => _query = value),
        ),
        const SizedBox(height: 16),

        if (searching)
          _SearchResults(results: results, onOpen: _open)
        else ...[
          // Topic selector
          FCSegmentedTabs(
            tabs: [for (final t in eduTopics) t.name],
            index: _topicIndex,
            onChanged: (i) => setState(() => _topicIndex = i),
          ),
          const SizedBox(height: 24),

          // Video lessons
          _SectionCard(
            title: 'Video Lessons',
            description: 'Short, engaging videos to explain key concepts.',
            items: videos,
            type: EduDocType.video,
            onOpen: _open,
          ),
          const SizedBox(height: 16),

          // Reading resources
          _SectionCard(
            title: 'Reading Resources',
            description: 'Curated articles and guides from trusted experts.',
            items: articles,
            type: EduDocType.article,
            onOpen: _open,
          ),
        ],
      ],
    );
  }
}

/// Live search results: a list of matched videos/articles, or an empty state.
class _SearchResults extends StatelessWidget {
  const _SearchResults({required this.results, required this.onOpen});

  final List<EduSearchDoc> results;
  final ValueChanged<String> onOpen;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;

    if (results.isEmpty) {
      return FCCard(
        child: FCCardContent(
          padding: const EdgeInsets.all(32),
          child: Column(
            children: [
              Icon(Icons.search_off, size: 40, color: c.mutedForeground),
              const SizedBox(height: 16),
              Text(
                'No results found. Try a different search.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  color: c.mutedForeground,
                  height: 1.5,
                ),
              ),
            ],
          ),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < results.length; i++) ...[
          if (i > 0) const SizedBox(height: 12),
          _ContentTile(
            title: results[i].title,
            subtitle: results[i].topicName,
            type: results[i].type,
            onTap: () => onOpen(results[i].url),
          ),
        ],
      ],
    );
  }
}

/// A card grouping a topic's items (videos or articles).
class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.title,
    required this.description,
    required this.items,
    required this.type,
    required this.onOpen,
  });

  final String title;
  final String description;
  final List<EduItem> items;
  final EduDocType type;
  final ValueChanged<String> onOpen;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          FCCardHeader(
            children: [
              FCCardTitle(title),
              FCCardDescription(description),
            ],
          ),
          FCCardContent(
            child: items.isEmpty
                ? Text(
                    'No content yet.',
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      color: c.mutedForeground,
                    ),
                  )
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (var i = 0; i < items.length; i++) ...[
                        if (i > 0) const SizedBox(height: 12),
                        _ContentTile(
                          title: items[i].title,
                          type: type,
                          onTap: () => onOpen(items[i].url),
                        ),
                      ],
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}

/// A single tappable content row: leading type icon, title (+ optional
/// subtitle), a Video/Reading badge, and a trailing open indicator.
class _ContentTile extends StatelessWidget {
  const _ContentTile({
    required this.title,
    required this.type,
    required this.onTap,
    this.subtitle,
  });

  final String title;
  final String? subtitle;
  final EduDocType type;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final isVideo = type == EduDocType.video;
    final accent = isVideo ? FCPalette.red600 : FCPalette.blue600;

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: c.background,
          borderRadius: FCRadii.mdAll,
          border: Border.all(color: c.border, width: 1),
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: accent.withValues(alpha: 0.1),
                borderRadius: FCRadii.mdAll,
              ),
              child: Icon(
                isVideo ? Icons.play_arrow_rounded : Icons.menu_book_outlined,
                color: accent,
                size: 22,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      fontWeight: FCFontWeights.semibold,
                      color: c.cardForeground,
                      height: 1.3,
                    ),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle!,
                      style: TextStyle(
                        fontSize: FCFontSizes.xs,
                        color: c.mutedForeground,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 8),
            FCBadge(
              isVideo ? 'Video' : 'Reading',
              variant: FCBadgeVariant.secondary,
            ),
            const SizedBox(width: 8),
            Icon(Icons.open_in_new, color: c.mutedForeground, size: 16),
          ],
        ),
      ),
    );
  }
}
