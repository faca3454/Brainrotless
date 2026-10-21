package com.brainrotless.app.screens.proto

import com.brainrotless.app.screens.Shots
import org.junit.Test

/** Paso 2: tres direcciones de rediseño (Inicio y una pantalla del panel), para elegir una. */
class ProtoShots : Shots() {
    private val looks = listOf(BASE, DARK)

    @Test fun aInicio() = shot("a_inicio", looks) { DirA.home(it) }
    @Test fun aCompromiso() = shot("a_compromiso", looks) { DirA.commitment(it) }
    @Test fun bInicio() = shot("b_inicio", looks) { DirB.home(it) }
    @Test fun bCompromiso() = shot("b_compromiso", looks) { DirB.commitment(it) }
    @Test fun cInicio() = shot("c_inicio", looks) { DirC.home(it) }
    @Test fun cDormir() = shot("c_dormir", looks) { DirC.sleep(it) }
}
