package pl.luczka.todaywas.core.domain.model

class JournalEntryNotFoundException(
    id: String,
) : Exception("No journal entry with id $id")
