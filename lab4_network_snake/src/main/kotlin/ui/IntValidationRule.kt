package onlinesnake.ui

fun interface IntValidationRule {
    fun validate(property: Int): Boolean
}