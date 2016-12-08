CREATE TABLE IF NOT EXISTS job (
	-- NB: *not* a AUTOINCREMENT because externally defined (:facepalm:)
	id INTEGER PRIMARY KEY ON CONFLICT ABORT,
	json TEXT,
	state INTEGER DEFAULT 0
);
