// PEM knowledge graph: one uniqueness constraint per label on its key property
// (kag-ontology skill, sections 2 and 5). Idempotent: safe to run any number of times.
// Statements are separated by semicolons; lines starting with // are comments.

// --- Legacy fact layer
CREATE CONSTRAINT program_name_unique IF NOT EXISTS FOR (n:Program) REQUIRE n.name IS UNIQUE;
CREATE CONSTRAINT paragraph_id_unique IF NOT EXISTS FOR (n:Paragraph) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT copybook_name_unique IF NOT EXISTS FOR (n:Copybook) REQUIRE n.name IS UNIQUE;
CREATE CONSTRAINT record_id_unique IF NOT EXISTS FOR (n:Record) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT field_id_unique IF NOT EXISTS FOR (n:Field) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT dataset_dsn_unique IF NOT EXISTS FOR (n:Dataset) REQUIRE n.dsn IS UNIQUE;
CREATE CONSTRAINT job_name_unique IF NOT EXISTS FOR (n:Job) REQUIRE n.name IS UNIQUE;
CREATE CONSTRAINT step_id_unique IF NOT EXISTS FOR (n:Step) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT transaction_id_unique IF NOT EXISTS FOR (n:Transaction) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT antipattern_id_unique IF NOT EXISTS FOR (n:AntiPattern) REQUIRE n.id IS UNIQUE;

// --- Knowledge layer
CREATE CONSTRAINT businessrule_ruleid_unique IF NOT EXISTS FOR (n:BusinessRule) REQUIRE n.ruleId IS UNIQUE;
CREATE CONSTRAINT keyfunctionality_id_unique IF NOT EXISTS FOR (n:KeyFunctionality) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT defect_id_unique IF NOT EXISTS FOR (n:Defect) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT question_id_unique IF NOT EXISTS FOR (n:Question) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT regulation_id_unique IF NOT EXISTS FOR (n:Regulation) REQUIRE n.id IS UNIQUE;

// --- Delivery layer (loaded in later phases; constraints created now so no schema change is needed later)

// --- Lookup indexes used by the standard read queries
CREATE INDEX businessrule_program IF NOT EXISTS FOR (n:BusinessRule) ON (n.program);
CREATE INDEX businessrule_verification IF NOT EXISTS FOR (n:BusinessRule) ON (n.verification);
