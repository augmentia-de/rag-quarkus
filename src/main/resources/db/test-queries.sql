-- ============================================================
-- GRAPHRAG TEST-QUERIES: 3 Schwierigkeitsstufen
-- Reichweite: Chunk-Retrieval → Graph-Traversal → Multi-Hop Reasoning
-- ============================================================

-- ============================================================
-- LEVEL 1: EINFACH — Direkter Abruf (Single Chunk, Exact Match)
-- ============================================================

-- Q1: "Wer gründete Nexora?"
-- Erwartet: chunk-d2-001 → "Dr. Elena Voss gründete Nexora 2014"
-- Graph-Node: node-2 (Dr. Elena Voss) — edge-1 (FOUNDED) → node-1 (Nexora)

-- Q2: "Was ist NexoraCore?"
-- Erwartet: chunk-d5-001 → Hauptproduktbeschreibung
-- Graph-Node: node-5 (NexoraCore)

-- Q3: "Wann wurde Nexora gegründet?"
-- Erwartet: chunk-d1-001 → "2014 von Dr. Elena Voss in Berlin"
-- Graph-Node: node-1 (Nexora Corporation)

-- Q4: "Was ist RiskShield AI?"
-- Erwartet: chunk-d6-001 → Betrugserkennungsmodul, 99,7% Genauigkeit
-- Graph-Node: node-6 (RiskShield AI)

-- Q5: "Ist Marcus Chen CTO?"
-- Erwartet: chunk-d3-001 → "Marcus Chen ist seit 2018 CTO"
-- Graph-Node: node-3 (Marcus Chen) — edge-3 (CTO_OF) → node-1

-- ============================================================
-- LEVEL 2: MITTEL — Graph-Traversal (2 Hops, Join über Edges)
-- ============================================================

-- Q6: "Wer arbeitet bei Nexora und wo hat er/sie vorher gearbeitet?"
-- Erwarteter Graph-Pfad (Hop 1 → Hop 2):
--   Dr. Elena Voss → (CEO_OF) → Nexora → ←(FOUNDED)← Dr. Elena Voss
--   Marcus Chen → (CTO_OF) → Nexora → ←(WORKED_AT)← SAP
--   Sarah Lindström → (CFO_OF) → Nexora → ←(WORKED_AT)← Deutsche Börse
-- Relevante Chunks: d2-001, d3-001, d4-001

-- Q7: "Welche Produkte bietet Nexora an?"
-- Erwartet: Graph-Expansion über HAS_MODULE
--   NexoraCore (node-5) → (HAS_MODULE) → RiskShield AI (node-6)
-- Relevante Chunks: d5-001, d6-001, d7-001

-- Q8: "Welche Tochterunternehmen hat Nexora und wo sitzen diese?"
-- Erwartet:
--   Nexora → (SUBSIDIARY_OF) ← Nexora Labs (München)
--   Nexora → (SUBSIDIARY_OF) ← Nexora Swiss AG (Zürich)
-- Relevante Chunks: d8-001, d9-001, d1-001

-- Q9: "Wer war vor Marcus Chen bei SAP?"
-- Trickfrage — wird als UNANSWERABLE / ABSTAIN erwartet,
-- weil kein höher- bzw. niederrangiger SAP-Mitarbeiter im Graph existiert.
-- Graph-Check: node-3 (Marcus Chen) → WORKED_AT → SAP (node-13) — keine weiteren SAP-Nodes

-- Q10: "Was ist der Zusammenhang zwischen Elena Voss und Google DeepMind?"
-- Erwartet: 
--   Dr. Elena Voss (node-2) → (WORKED_AT) → Google DeepMind (node-12)
-- Relevante Chunks: d2-001

-- ============================================================
-- LEVEL 3: SCHWER — Multi-Hop Reasoning (3+ Hops, Indirect Relations)
-- ============================================================

-- Q11: "Wie hängt Sarah Lindström mit der Deutschen Börse zusammen und was macht sie jetzt?"
-- Erwarteter Pfad (3 Hops):
--   Sarah Lindström → (CFO_OF) → Nexora → ←(SUBSIDIARY_OF)← Nexora Labs
--   + Sarah Lindström → (WORKED_AT) → Deutsche Börse
-- Erforderliche Graphentitäten: CFO, Deutsche Börse, Nexora
-- Relevante Chunks: d4-001, d8-001

-- Q12: "Welche Beziehungen bestehen zwischen Nexora Labs und der Deutschen Bank?"
-- Erwarteter Pfad (indirekt, 3 Hops):
--   Nexora Labs → (SUBSIDIARY_OF) → Nexora → (PARTNER_OF) → Deutsche Bank
-- Hinweis: Kein direkter Edge zwischen Labs und DB — muss traversiert werden
-- Relevante Chunks: d8-001, d1-001, d10-001

-- Q13: "Welche Führungskräfte haben bei Google oder SAP gearbeitet bevor sie zu Nexora kamen?"
-- Erwartete Query-Zerlegung:
--   Sub-Frage 1: "Wer arbeitete bei Google?" → Dr. Elena Voss
--   Sub-Frage 2: "Wer arbeitete bei SAP?" → Marcus Chen
-- Erforderlich: beide WORKED_AT-Edges auflösen
-- Relevante Chunks: d2-001, d3-001

-- Q14: "Was verbindet alle Mitarbeiter von Nexora miteinander?"
-- Erwartet: Gemeinsamer Nenner ist Nexora Corporation als Organisation
-- Graph-Ergebnis:
--   node-2 (Voss) → CEO_OF → node-1 (Nexora) ← CTO_OF ← node-3 (Chen)
--   node-1 (Nexora) ← CFO_OF ← node-4 (Lindström)
-- Relevante Chunks: d1-001, d2-001, d3-001, d4-001

-- Q15: "Welche Ereignisse fanden 2024 statt und wer war beteiligt?"
-- Erwartet:
--   NexoraTech Conference 2024 (node-11, EVENT)
--     → node-2 (Dr. Elena Voss) als SPEAKER_AT
--     → node-3 (Marcus Chen) als PRESENTER_AT
-- Relevante Chunks: d12-001, d2-001, d3-001, d7-001

-- Q16: "Welche Produkte nutzt die Deutsche Bank über die Partnerschaft?"
-- Erwartet (2 Hops):
--   Deutsche Bank → (PARTNER_OF) → Nexora → (PRODUCES) → NexoraCore
--   + Deutsche Bank → (PARTNER_OF) → Nexora → (PRODUCES) → RiskShield AI
-- Relevante Chunks: d5-001, d6-001, d10-001

-- Q17: "Welche Unternehmen haben Nexora-Mitarbeiter zuvor verlassen?"
-- Erwartet:
--   Google DeepMind → (EMPLOYED) → Dr. Elena Voss
--   SAP → (EMPLOYED) → Marcus Chen
--   Deutsche Börse AG → (EMPLOYED) → Sarah Lindström
-- Relevante Chunks: d2-001, d3-001, d4-001

-- Q18: "Wer präsentierte NexoraCore 3.0 bei der NexoraTech Conference?"
-- Erwartet (3 Hops):
--   NexoraCore 3.0 release → (PRESENTED_AT) → NexoraTech Conference 2024
--   → node-3 (Marcus Chen) als Präsentator
-- Relevante Chunks: d7-001, d12-001, d3-001

-- ============================================================
-- ERWARTETE GRAPH-PFADE (für automatische Eval)
-- ============================================================

-- Q6 Pfad: 
--   node-2 → CEO_OF → node-1, node-2 → WORKED_AT → node-12
--   node-3 → CTO_OF → node-1, node-3 → WORKED_AT → node-13
--   node-4 → CFO_OF → node-1, node-4 → WORKED_AT → node-14
-- Max Hops: 2, expectedNodes: 7 (2,1,12,3,13,4,14), expectedEdges: 6

-- Q7 Pfad:
--   node-1 (Nexora) → HAS_MODULE → node-5, node-6
--   Optional: node-7, node-8 (Subsidiaries → SUBSIDIARY_OF → Nexora)
-- Max Hops: 2, expectedNodes: mindestens 3 (1,5,6)

-- Q8 Pfad:
--   node-1 → SUBSIDIARY_OF ← node-7, node-8
-- Max Hops: 1 (direkt), expectedNodes: 3 (1,7,8), expectedEdges: 2

-- Q12 Pfad:
--   node-7 → SUBSIDIARY_OF → node-1 → PARTNER_OF → node-9
--   Hop 1: node-7 → SUBSIDIARY_OF → node-1
--   Hop 2: node-1 → PARTNER_OF → node-9
-- Max Hops: 2, expectedPath: [node-7, node-1, node-9]

-- Q14 Pfad:
--   node-2 → CEO_OF → node-1, node-3 → CTO_OF → node-1, node-4 → CFO_OF → node-1
--   Max Hops: 1, expectedNodes: 4 (1,2,3,4), expectedEdges: 3

-- Q18 Pfad:
--   node-7 → HAS_MODULE → node-5 → (context: NexoraCore 3.0)
--   node-3 → PRESENTER_AT → node-11 (NexoraTech Conference)
--   Beide Pfade vereinigen → Antwort: Marcus Chen präsentierte NexoraCore 3.0
--   Max Hops: 3
