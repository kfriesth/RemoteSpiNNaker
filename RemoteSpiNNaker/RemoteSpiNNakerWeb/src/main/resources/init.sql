CREATE TABLE IF NOT EXISTS job (
	-- NB: *not* a AUTOINCREMENT because externally defined (:facepalm:)
	id INTEGER PRIMARY KEY ON CONFLICT ABORT,
	json TEXT,
	state INTEGER DEFAULT 0,
	executer TEXT, -- TODO make the executers their own table
	numCores INTEGER DEFAULT 0,
	resourceUsage INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS jobProvenance (
	id INTEGER REFERENCES job ON DELETE CASCADE,
	provKey TEXT NOT NULL,
	provValue TEXT NOT NULL,
	PRIMARY KEY (id, provKey ASC)
);

CREATE UNIQUE INDEX IF NOT EXISTS jobProvenanceKeyIndex ON jobProvenance (id, provKey);
