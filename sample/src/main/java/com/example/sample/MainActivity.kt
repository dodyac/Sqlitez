package com.example.sample

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.acxdev.sqlitez.SqliteZ
import com.acxdev.sqlitez.common.DatabaseNameHolder
import com.acxdev.sqlitez.read.Condition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random


data class Person(
    val name: String,
    val gender: Gender,
    val age: Int
)

enum class Gender {
    male, female
}

data class PersonOnly(
    val name: String
)

class MainActivity : AppCompatActivity() {

    val sqliteZ = SqliteZ(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val names = listOf("Alice", "Bob", "Charlie", "Dave", "Eve") // Add more names as needed

        fun randomName() = names.random()

        fun randomGender() = if (Random.nextBoolean()) Gender.male else Gender.female

        val randomPerson = Person(
            name = randomName(),
            gender = randomGender(),
            age = Random.nextInt(18, 65) // Example age range
        )
        lifecycleScope.launch(Dispatchers.IO) {
            sqliteZ.insert(randomPerson)
            val condition = Condition()
            condition.orderBy = Condition.OrderBy.Ascending(Person::age)
            condition.values = listOf(
                Condition.Value(Person::name, "Bob", Condition.Value.Command.Equal),
                Condition.Value(Person::age, 22, Condition.Value.Command.Equal)
            )
//            println(sqliteZ.getAll<Person>(condition))
//            println(sqliteZ.getAllMapOf<Person, PersonOnly>(condition))
            println(sqliteZ.getAllMapOf<Person, PersonOnly>())
        }
    }
}