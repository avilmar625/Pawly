package com.pawly.app.content

/**
 * Editorial starter library. External organisations own and maintain their pages.
 * Pawly does not claim to certify or endorse every external resource.
 */
internal object PawlyLearningLibrary {
    val guides = listOf(
        LearningGuide(
            id = "body-language",
            emoji = "🐕",
            title = "Aprende a leer su lenguaje",
            subtitle = "Observa el conjunto, no una señal aislada",
            points = listOf(
                "Mira postura, movimiento, mirada, orejas y contexto en conjunto.",
                "Bostezar, lamerse el hocico o apartar la mirada pueden aparecer ante incomodidad, pero no significan siempre lo mismo.",
                "Si intenta alejarse, se queda rígido o busca esconderse, aumenta la distancia y dale una salida.",
                "No fuerces saludos con personas o perros: poder elegir también es bienestar."
            ),
            note = "Si aparecen miedo intenso, agresividad o cambios repentinos de conducta, consulta con un veterinario y un profesional cualificado en comportamiento."
        ),
        LearningGuide(
            id = "positive-training",
            emoji = "🎓",
            title = "Adiestramiento amable",
            subtitle = "Refuerza lo que quieres que se repita",
            points = listOf(
                "Empieza en un entorno tranquilo y con pocas distracciones.",
                "Premia la conducta deseada justo cuando ocurre, con algo que motive a tu perro.",
                "Practica en sesiones cortas y termina antes de que se canse o se frustre.",
                "Aumenta la dificultad poco a poco: primero duración, después distancia y finalmente distracciones.",
                "Evita intimidación, dolor y herramientas aversivas; pueden empeorar el miedo y la respuesta emocional."
            ),
            note = "Para problemas complejos, busca un educador con formación contrastable, métodos respetuosos y experiencia demostrable."
        ),
        LearningGuide(
            id = "sniffing",
            emoji = "🌿",
            title = "Paseos con tiempo para olfatear",
            subtitle = "No todos los paseos tienen que ser kilómetros",
            points = listOf(
                "Reserva parte del paseo para explorar y olfatear sin prisa, en una zona segura.",
                "Permite elegir el ritmo y cambiar de dirección cuando sea posible.",
                "Alterna rutas conocidas con novedades asumibles para tu perro.",
                "Comprueba temperatura, suelo, tráfico, normativa local y distancia respecto a otros perros.",
                "Valora el paseo por cómo vuelve tu perro, no solo por los pasos o kilómetros."
            ),
            note = "Adapta duración y actividad a edad, condición física, salud y preferencias individuales."
        ),
        LearningGuide(
            id = "adoption",
            emoji = "🏡",
            title = "Los primeros días tras una adopción",
            subtitle = "Seguridad, paciencia y rutinas predecibles",
            points = listOf(
                "Prepara una zona tranquila donde pueda descansar sin que nadie le moleste.",
                "Mantén horarios sencillos y evita visitas, fiestas y demasiadas novedades al principio.",
                "Deja que se acerque por iniciativa propia; no fuerces caricias ni presentaciones.",
                "Usa doble sistema de seguridad en los primeros paseos si la protectora lo recomienda.",
                "Haz las presentaciones con otros animales de forma gradual y supervisada."
            ),
            note = "Coordina con la protectora y el veterinario la documentación, identificación, revisiones y necesidades particulares."
        ),
        LearningGuide(
            id = "choose-professional",
            emoji = "🤝",
            title = "Cómo elegir un buen educador",
            subtitle = "La confianza y el bienestar son prioritarios",
            points = listOf(
                "Pregunta por formación, experiencia y actualización profesional.",
                "Debe observar al perro y su entorno antes de proponer un plan.",
                "Explica objetivos realistas, pasos, límites y cómo medir avances.",
                "Permite que el perro se aleje y respeta señales de estrés.",
                "Desconfía de garantías de resultados, castigos, miedo o explicaciones basadas en dominar al perro."
            ),
            note = "Si el cambio de conducta puede estar relacionado con dolor o enfermedad, la primera parada debe ser el veterinario."
        ),
        LearningGuide(
            id = "enrichment",
            emoji = "🧩",
            title = "Enriquecimiento en casa",
            subtitle = "Pequeñas actividades para activar mente y olfato",
            points = listOf(
                "Esconde parte de su ración en varios puntos fáciles y seguros.",
                "Prueba alfombras olfativas o juguetes rellenables adecuados a su tamaño.",
                "Rota los juegos para mantener el interés sin acumular demasiados estímulos.",
                "Supervisa los materiales nuevos y retira piezas que pueda romper o tragar.",
                "Incluye descanso: estimular no significa mantenerle ocupado todo el día."
            ),
            note = "Adapta cada juego a sus capacidades y evita objetos o alimentos que puedan ser peligrosos para él."
        )
    )

    val organisations = listOf(
        ContentLink("affinity", "💚", "Fundación Affinity", "Estudios, educación y sensibilización sobre el vínculo entre personas y animales.", "https://www.fundacion-affinity.org/", "Bienestar"),
        ContentLink("faada", "🐾", "FAADA", "Protección animal, campañas, recursos y formas de colaboración.", "https://faada.org/", "Protección"),
        ContentLink("refugio", "🏠", "El Refugio", "Adopción, rescate y voluntariado en una entidad de protección animal.", "https://elrefugio.org/", "Adopción"),
        ContentLink("devuelta", "🔎", "De Vuelta a Casa", "Directorio de protectoras, animales en adopción y recursos para animales perdidos o encontrados.", "https://www.devueltaacasa.es/", "Directorio"),
        ContentLink("cangossos", "🎥", "Fundació Gossos", "Noticias, videoblog y contenidos sobre educación y bienestar canino.", "https://cangossos.es/es/news/", "Educación"),
        ContentLink("doogweb", "📰", "Doogweb", "Actualidad y divulgación sobre comportamiento, educación y vida con perros.", "https://www.doogweb.es/", "Actualidad"),
        ContentLink("rsce", "🏅", "Real Sociedad Canina de España", "Información sobre el mundo canino, actividades y educación.", "https://www.rsce.es/", "Cultura canina")
    )

    val videos = listOf(
        VideoCollection("Educación en positivo", "Ejercicios básicos, aprendizaje y convivencia respetuosa.", "adiestramiento canino en positivo ejercicios"),
        VideoCollection("Lenguaje y comunicación", "Señales de calma, comunicación y comportamiento canino.", "lenguaje corporal perros etología canina"),
        VideoCollection("Olfato y enriquecimiento", "Juegos de olfato y actividades sencillas para casa.", "juegos de olfato perros enriquecimiento ambiental"),
        VideoCollection("Adopción y adaptación", "Experiencias y consejos para acompañar una adopción.", "adopción perros adaptación primeros días protectora"),
        VideoCollection("Deporte y aventura", "Senderismo, canicross y actividades compartidas.", "senderismo con perros canicross seguridad")
    )
}
