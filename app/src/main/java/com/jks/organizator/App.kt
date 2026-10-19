package com.jks.organizator

import android.app.Application
import com.jks.organizator.data.Category
import com.jks.organizator.data.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            val w = Graph.db.wallet()
            if (w.categories().isEmpty()) {
                listOf("Cigare", "Kafa", "Odjeća", "Izlazak", "Gorivo").forEach { w.insertCategory(Category(kind = "spent", name = it)) }
                listOf("Plata", "Honorar", "Prodaja").forEach { w.insertCategory(Category(kind = "earned", name = it)) }
            }
        }
    }
}
