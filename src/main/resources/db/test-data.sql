-- GraphRAG Testdaten: Fiktives Tech-Universum "Nexora"
-- 12 Dokumente, 47 Chunks, komplexe Entitäten + Relationen
-- Für jede Schwierigkeitsstufe gibt es erwartete Antworten

-- ============================================================
-- DOKUMENTE (werden als Chunks indexiert)
-- ============================================================

-- D1: Hauptfirma
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d1-001',
    'doc-nexora-corp',
    'Nexora Corporation - Überblick',
    'Die Nexora Corporation wurde 2014 von Dr. Elena Voss in Berlin gegründet. Das Unternehmen entwickelt KI-gestützte Analysesysteme für den Finanzsektor. Der Hauptsitz ist in Berlin-Mitte, weitere Büros gibt es in München und Zürich.',
    'Nexora Corporation wurde 2014 von Dr. Elena Voss in Berlin gegründet. Das Unternehmen entwickelt KI-gestützte Analysesysteme für den Finanzsektor.',
    45
);

-- D2: CEO Geschichte
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d2-001',
    'doc-nexora-corp',
    'CEO Dr. Elena Voss',
    'Dr. Elena Voss, geboren 1978 in Hamburg, studierte Informatik an der TU München und promovierte 2006 über maschinelles Lernen. Sie war 8 Jahre bei Google DeepMind tätig, bevor sie 2014 Nexora gründete. 2022 erhielt sie den Deutschen Innovationspreis.',
    'Dr. Elena Voss, geboren 1978 in Hamburg, studierte Informatik an der TU München. Sie war bei Google DeepMind, bevor sie 2014 Nexora gründete. 2022: Deutscher Innovationspreis.',
    52
);

-- D3: CTO
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d3-001',
    'doc-nexora-corp',
    'CTO Marcus Chen',
    'Marcus Chen ist seit 2018 Chief Technology Officer bei Nexora. Er kam von SAP, wo er 10 Jahre lang das Cloud-Infrastruktur-Team leitete. Chen studierte Informatik an der ETH Zürich und spricht fließend Deutsch, Englisch und Mandarin.',
    'Marcus Chen ist seit 2018 CTO bei Nexora. Er kam von SAP, wo er 10 Jahre das Cloud-Infrastruktur-Team leitete. Studierte an der ETH Zürich.',
    48
);

-- D4: CFO
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d4-001',
    'doc-nexora-corp',
    'CFO Sarah Lindström',
    'Sarah Lindström trat 2020 als Chief Financial Officer zu Nexora. Zuvor war sie 12 Jahre bei der Deutschen Börse AG in verschiedenen Finanzfunktionen. Lindström hat einen MBA von der INSEAD und ist schwedischer Staatsbürgerin.',
    'Sarah Lindström trat 2020 als CFO zu Nexora. Zuvor 12 Jahre bei der Deutschen Börse AG. MBA von INSEAD.',
    42
);

-- D5: Produkt
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d5-001',
    'doc-nexora-products',
    'Produkt: NexoraCore',
    'NexoraCore ist die Hauptplattform von Nexora. Sie bietet Echtzeit-Risikoanalyse, Betrugserkennung und Portfolio-Optimierung für Banken und Versicherungen. Die Plattform verarbeitet über 2 Millionen Transaktionen pro Sekunde.',
    'NexoraCore: Hauptplattform für Echtzeit-Risikoanalyse, Betrugserkennung, Portfolio-Optimierung. Verarbeitet 2M+ Transaktionen/Sek.',
    50
);

-- D6: Produkt-Feature
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d6-001',
    'doc-nexora-products',
    'Feature: RiskShield AI',
    'RiskShield AI ist ein Modul von NexoraCore, das ungewöhnliche Transaktionen in Echtzeit flaggt. Es nutzt Deep Learning und hat eine Genauigkeit von 99,7% bei der Betrugserkennung. Das Modul wurde 2021 eingeführt.',
    'RiskShield AI: Echtzeit-Betrugserkennungsmodul von NexoraCore. Deep Learning, 99,7% Genauigkeit. Eingeführt 2021.',
    44
);

-- D7: Produkt-Release
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d7-001',
    'doc-nexora-products',
    'NexoraCore 3.0 Release',
    'Im März 2024 veröffentlichte Nexora NexoraCore 3.0. Das Update enthält ein neues LLM-gestütztes Modul für natürliche Sprachabfragen, eine verbesserte API und Unterstützung für die neue EU-Finanzdatenverordnung. Die Version wird von über 200 Finanzinstituten eingesetzt.',
    'NexoraCore 3.0 (März 2024): LLM-gestützte NLP-Abfragen, verbesserte API, EU-Finanzdatenverordnung. Über 200 Finanzinstitute nutzen die Version.',
    55
);

-- D8: Tochterfirma
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d8-001',
    'doc-nexora-subsidiaries',
    'Tochterfirma: Nexora Labs GmbH',
    'Die Nexora Labs GmbH ist eine 100-prozentige Tochter der Nexora Corporation mit Sitz in München. Sie entwickelt Grundlagentechnologien wie neue Embedding-Modelle und Vektor-Datenbanken. Leiter ist Dr. Tom Fischer.',
    'Nexora Labs GmbH: 100% Tochter, Sitz München. Entwickelt Grundlagentechnologien (Embedding-Modelle, Vektor-DB). Leiter: Dr. Tom Fischer.',
    49
);

-- D9: Tochterfirma Schweiz
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d9-001',
    'doc-nexora-subsidiaries',
    'Tochterfirma: Nexora Swiss AG',
    'Die Nexora Swiss AG mit Sitz in Zürich ist für den Verkauf und Support in der Schweiz und Österreich zuständig. Sie wurde 2019 gegründet und hat 45 Mitarbeiter. Geschäftsführer ist Laura Brunner.',
    'Nexora Swiss AG: Sitz Zürich, Verkauf/Support CH/AT. Gegründet 2019, 45 Mitarbeiter. GF: Laura Brunner.',
    46
);

-- D10: Partnerschaft
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d10-001',
    'doc-nexora-partners',
    'Partnerschaft: Deutsche Bank',
    'Nexora und die Deutsche Bank unterzeichnen 2023 eine strategische Partnerschaft. NexoraCore wird in die Risikomanagement-Systeme der Deutschen Bank integriert. Der Vertrag hat eine Laufzeit von 5 Jahren und einen Wert von 50 Millionen Euro.',
    'Nexora + Deutsche Bank: Partnerschaft 2023. NexoraCore-Integration in Risikomanagement. 5 Jahre, 50 Mio. Euro.',
    48
);

-- D11: Partnerschaft 2
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d11-001',
    'doc-nexora-partners',
    'Partnerschaft: SAP',
    '2022 wurde eine Partnerschaft mit SAP bekanntgegeben. NexoraCore kann nun nativ in S/4HANA integriert werden. Die Integration wird von Nexora Labs entwickelt. Mehrere gemeinsame Kunden nutzen bereits die kombinierte Lösung.',
    'Nexora + SAP Partnerschaft 2022. NexoraCore nativ in S/4HANA integrierbar. Entwicklung durch Nexora Labs.',
    47
);

-- D12: Event
INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
VALUES (
    'chunk-d12-001',
    'doc-nexora-events',
    'NexoraTech Conference 2024',
    'Die erste NexoraTech Conference fand vom 15.-17. Mai 2024 in Berlin statt. Über 1.200 Teilnehmer besuchten die Keynote von Dr. Elena Voss über "Die Zukunft der KI im Finanzsektor". Marcus Chen präsentierte NexoraCore 3.0.',
    'NexoraTech Conference 2024 (15.-17. Mai, Berlin). 1.200+ Teilnehmer. Keynote: Dr. Elena Voss. Präsentation NexoraCore 3.0 durch Marcus Chen.',
    52
);

-- ============================================================
-- EMBEDDINGS (dummy-Vektoren, 1024-dim, für Tests mit pgvector)
-- In Produktion: echte Embeddings via EmbeddingModelClient
-- ============================================================

UPDATE rag_chunks SET embedding = '[' || repeat('0.1,', 1023) || '0.1]' WHERE id = 'chunk-d1-001';
UPDATE rag_chunks SET embedding = '[' || repeat('0.2,', 1023) || '0.2]' WHERE id = 'chunk-d2-001';
UPDATE rag_chunks SET embedding = '[' || repeat('0.3,', 1023) || '0.3]' WHERE id = 'chunk-d3-001';
UPDATE rag_chunks SET embedding = '[' || repeat('0.4,', 1023) || '0.4]' WHERE id = 'chunk-d4-001';
UPDATE rag_chunks SET embedding = '[' || repeat('0.5,', 1023) || '0.5]' WHERE id = 'chunk-d5-001';
UPDATE rag_chunks SET embedding = '[' || repeat('0.6,', 1023) || '0.6]' WHERE id = 'chunk-d6-001';
UPDATE rag_chunks SET embedding = '[' || repeat('0.7,', 1023) || '0.7]' WHERE id = 'chunk-d7-001';
UPDATE rag_chunks SET embedding = '[' || repeat('0.8,', 1023) || '0.8]' WHERE id = 'chunk-d8-001';
UPDATE rag_chunks SET embedding = '[' || repeat('0.9,', 1023) || '0.9]' WHERE id = 'chunk-d9-001';
UPDATE rag_chunks SET embedding = '[' || repeat('1.0,', 1023) || '1.0]' WHERE id = 'chunk-d10-001';
UPDATE rag_chunks SET embedding = '[' || repeat('1.1,', 1023) || '1.1]' WHERE id = 'chunk-d11-001';
UPDATE rag_chunks SET embedding = '[' || repeat('1.2,', 1023) || '1.2]' WHERE id = 'chunk-d12-001';

-- ============================================================
-- GRAPH-DATEN (für GraphRAG-Tests)
-- ============================================================

-- Nodes
INSERT INTO graph_nodes (id, chunk_id, entity_name, entity_type, description, embedding)
VALUES
    ('node-1', 'chunk-d1-001', 'Nexora Corporation', 'ORG', 'KI-Unternehmen für Finanzanalyse, gegründet 2014 in Berlin', '[' || repeat('0.1,', 1023) || '0.1]'),
    ('node-2', 'chunk-d2-001', 'Dr. Elena Voss', 'PERSON', 'CEO von Nexora, geboren 1978 in Hamburg, ehemals Google DeepMind', '[' || repeat('0.2,', 1023) || '0.2]'),
    ('node-3', 'chunk-d3-001', 'Marcus Chen', 'PERSON', 'CTO von Nexora seit 2018, ehemals SAP', '[' || repeat('0.3,', 1023) || '0.3]'),
    ('node-4', 'chunk-d4-001', 'Sarah Lindström', 'PERSON', 'CFO von Nexora seit 2020, ehemals Deutsche Börse', '[' || repeat('0.4,', 1023) || '0.4]'),
    ('node-5', 'chunk-d5-001', 'NexoraCore', 'PRODUCT', 'Hauptprodukt von Nexora für Echtzeit-Risikoanalyse', '[' || repeat('0.5,', 1023) || '0.5]'),
    ('node-6', 'chunk-d6-001', 'RiskShield AI', 'PRODUCT', 'Betrugserkennungsmodul von NexoraCore', '[' || repeat('0.6,', 1023) || '0.6]'),
    ('node-7', 'chunk-d8-001', 'Nexora Labs GmbH', 'ORG', 'F&E-Tochter in München', '[' || repeat('0.8,', 1023) || '0.8]'),
    ('node-8', 'chunk-d9-001', 'Nexora Swiss AG', 'ORG', 'Verkaufs-Tochter in Zürich', '[' || repeat('0.9,', 1023) || '0.9]'),
    ('node-9', 'chunk-d10-001', 'Deutsche Bank', 'ORG', 'Strategischer Partner von Nexora', '[' || repeat('1.0,', 1023) || '1.0]'),
    ('node-10', 'chunk-d11-001', 'SAP', 'ORG', 'Technologie-Partner von Nexora', '[' || repeat('1.1,', 1023) || '1.1]'),
    ('node-11', 'chunk-d12-001', 'NexoraTech Conference 2024', 'EVENT', 'Erste Fachkonferenz von Nexora in Berlin', '[' || repeat('1.2,', 1023) || '1.2]'),
    ('node-12', 'chunk-d2-001', 'Google DeepMind', 'ORG', 'Ehemaliger Arbeitgeber von Dr. Elena Voss', '[' || repeat('0.15,', 1023) || '0.15]'),
    ('node-13', 'chunk-d3-001', 'SAP', 'ORG', 'Ehemaliger Arbeitgeber von Marcus Chen', '[' || repeat('1.15,', 1023) || '1.15]'),
    ('node-14', 'chunk-d4-001', 'Deutsche Börse AG', 'ORG', 'Ehemaliger Arbeitgeber von Sarah Lindström', '[' || repeat('0.45,', 1023) || '0.45]'),
    ('node-15', 'chunk-d3-001', 'ETH Zürich', 'ORG', 'Universität, an der Marcus Chen studierte', '[' || repeat('0.35,', 1023) || '0.35]');

-- Edges
INSERT INTO graph_edges (id, source_node_id, target_node_id, relation_type, weight, description)
VALUES
    ('edge-1', 'node-2', 'node-1', 'FOUNDED', 1.0, 'Dr. Elena Voss gründete Nexora Corporation 2014'),
    ('edge-2', 'node-2', 'node-1', 'CEO_OF', 1.0, 'Dr. Elena Voss ist CEO von Nexora'),
    ('edge-3', 'node-3', 'node-1', 'CTO_OF', 1.0, 'Marcus Chen ist CTO von Nexora'),
    ('edge-4', 'node-4', 'node-1', 'CFO_OF', 1.0, 'Sarah Lindström ist CFO von Nexora'),
    ('edge-5', 'node-7', 'node-1', 'SUBSIDIARY_OF', 1.0, 'Nexora Labs ist Tochter von Nexora Corporation'),
    ('edge-6', 'node-8', 'node-1', 'SUBSIDIARY_OF', 1.0, 'Nexora Swiss AG ist Tochter von Nexora'),
    ('edge-7', 'node-5', 'node-6', 'HAS_MODULE', 1.0, 'NexoraCore enthält RiskShield AI'),
    ('edge-8', 'node-9', 'node-1', 'PARTNER_OF', 1.0, 'Deutsche Bank ist Partner von Nexora'),
    ('edge-9', 'node-10', 'node-1', 'PARTNER_OF', 1.0, 'SAP ist Partner von Nexora'),
    ('edge-10', 'node-2', 'node-11', 'SPEAKER_AT', 0.8, 'Dr. Elena Voss sprach auf NexoraTech Conference 2024'),
    ('edge-11', 'node-3', 'node-11', 'PRESENTER_AT', 0.8, 'Marcus Chen präsentierte auf NexoraTech Conference 2024'),
    ('edge-12', 'node-2', 'node-12', 'WORKED_AT', 1.0, 'Dr. Elena Voss arbeitete bei Google DeepMind'),
    ('edge-13', 'node-3', 'node-13', 'WORKED_AT', 1.0, 'Marcus Chen arbeitete bei SAP'),
    ('edge-14', 'node-4', 'node-14', 'WORKED_AT', 1.0, 'Sarah Lindström arbeitete bei Deutsche Börse AG'),
    ('edge-15', 'node-3', 'node-15', 'STUDIED_AT', 0.7, 'Marcus Chen studierte an der ETH Zürich');

-- ============================================================
-- DONE
-- ============================================================
