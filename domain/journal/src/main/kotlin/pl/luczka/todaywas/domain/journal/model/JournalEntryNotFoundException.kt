package pl.luczka.todaywas.domain.journal.model

class JournalEntryNotFoundException(
    id: String,
) : Exception("No journal entry with id $id")
