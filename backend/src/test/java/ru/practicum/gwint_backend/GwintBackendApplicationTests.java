package ru.practicum.gwint_backend;

import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import ru.practicum.gwint_backend.domain.model.CardId;
import ru.practicum.gwint_backend.infrastructure.catalog.JsonCardCatalog;


class GwintBackendApplicationTests {

	@DisplayName("Проверка загрузки каталога карт")
	@Test
	void checkDeserialisingCardsFromJsonFile() {
		var loader = new JsonCardCatalog();
		var cards = loader.getAll();
		assertNotNull(cards);
		assertEquals(14,cards.size());
		assertTrue(loader.contains(new CardId("warden")));
	}

}
