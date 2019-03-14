package org.projectforge.ui

import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Test

class UILayoutTest {
    @Test
    fun testBook() {
        var layout = UILayout("Buch bearbeiten")
                .add(UIGroup()
                        .add(UILabel("Titel", "title"))
                        .add(UIInput("title", 255, required = true, focus = true)))
                .add(UIGroup()
                        .add(UILabel("Autoren", "authors"))
                        .add(UIInput("authors", 255)))
                .add(UIRow()
                        .add(UICol(6)
                                .add(UIGroup()
                                        .add(UILabel("Typ", "type"))
                                        .add(UISelect("type")
                                                .add(UISelectValue("book", "Buch"))
                                                .add(UISelectValue("magazine", "Magazin")))
                                        .add(UICheckbox("favorite")))
                                .add(UIGroup()
                                        .add(UILabel("Veröffentlichungsjahr", "yearOfPublishing"))
                                        .add(UIInput("yearOfPublishing", 255)))
                                .add(UIGroup()
                                        .add(UILabel("Status", "status"))
                                        .add(UISelect("status")
                                                .add(UISelectValue("present", "vorhanden"))
                                                .add(UISelectValue("missed", "vermisst"))))
                                .add(UIGroup()
                                        .add(UILabel("Signatur", "signature"))
                                        .add(UIInput("signature", 255))))
                        .add(UICol(6)
                                .add(UIGroup()
                                        .add(UILabel("ISBN", "isbn"))
                                        .add(UIInput("isbn", 255)))
                                .add(UIGroup()
                                        .add(UILabel("Schlüsselwörter", "keywords"))
                                        .add(UIInput("keywords", 255)))
                                .add(UIGroup()
                                        .add(UILabel("Verlag", "publisher"))
                                        .add(UIInput("publisher", 255)))
                                .add(UIGroup()
                                        .add(UILabel("Herausgeber", "editor"))
                                        .add(UIInput("editor", 255)))))
                .add(UIGroup()
                        .add(UILabel("Zusammenfassung", "abstract"))
                        .add(UITextarea("abstact", 4000)))
                .add(UIGroup()
                        .add(UILabel("Bemerkung", "comment"))
                        .add(UITextarea("comment", 4000)))
        var gson = GsonBuilder().setPrettyPrinting().create()
        var jsonString = gson.toJson(layout)
        println(jsonString)
    }
}