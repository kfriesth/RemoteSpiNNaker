CREATE TABLE IF NOT EXISTS job (
	-- NB: *not* a AUTOINCREMENT because externally defined (:facepalm:)
	id INTEGER PRIMARY KEY ON CONFLICT ABORT,
	json TEXT,
	state INTEGER DEFAULT 0,
	executer TEXT, -- TODO make the executers their own table
	numCores INTEGER DEFAULT 0,
	resourceUsage INTEGER DEFAULT 0,
	temporaryDirectory TEXT,
	creation NUMERIC
);

CREATE TABLE IF NOT EXISTS jobProvenance (
	id INTEGER REFERENCES job ON DELETE CASCADE,
	provKey TEXT NOT NULL,
	provValue TEXT NOT NULL,
	PRIMARY KEY (id, provKey ASC)
);

CREATE UNIQUE INDEX IF NOT EXISTS jobProvenanceKeyIndex ON jobProvenance (id, provKey);

CREATE TABLE IF NOT EXISTS jobMachines (
	id INTEGER REFERENCES job ON DELETE CASCADE,
	machine TEXT
);

CREATE INDEX IF NOT EXISTS jobMachinesKeyIndex ON jobMachines (id);

CREATE TABLE IF NOT EXISTS xen (
	-- These are all UUIDs...
	id TEXT PRIMARY KEY ON CONFLICT ABORT,
	vm TEXT,
	disk TEXT,
	image TEXT,
	extraDisk TEXT,
	extraImage TEXT
);