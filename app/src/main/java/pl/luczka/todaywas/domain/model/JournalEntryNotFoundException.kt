package pl.luczka.todaywas.domain.model

class JournalEntryNotFoundException(
    id: String,
) : Exception("No journal entry with id $id")
