package ru.glonassunion.aerospace.scoutfence

interface ILorawanJsonHandler {
    fun HandleLorawanJSON(json: String?): Boolean
}