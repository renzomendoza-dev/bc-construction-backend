package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.InventoryTestConfig;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemImage;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest slice tests for ItemRepository, plus the cascade/orphanRemoval
 * behavior of Item.images (ItemImageRepository) since that relationship is
 * owned by Item and can't meaningfully be tested in isolation.
 *
 * <p>Requires an embedded test database (e.g. the h2database "h2" artifact,
 * test scope) on the classpath — @DataJpaTest replaces the configured
 * DataSource with an embedded one by default via
 * {@code @AutoConfigureTestDatabase}, and needs a driver to do so.
 *
 * <p>ASSUMPTIONS (repository method signatures / DTO-adjacent details not
 * given beyond "assume it has findBySku(String sku)"):
 * <ul>
 *   <li>{@code ItemRepository.findBySku(String)} returns
 *       {@code Optional<Item>}, matching the Optional-returning convention
 *       used by every other custom finder seen in this codebase.</li>
 *   <li>NOT NULL scenarios (5, 6) assert the thrown exception is either
 *       {@code DataIntegrityViolationException} (DB-level column constraint,
 *       translated by Spring) or {@code jakarta.validation.ConstraintViolationException}
 *       (Bean Validation, if @NotNull annotations + hibernate-validator are
 *       also present and intercept before the DB is even reached) — the
 *       CONTEXT only specified these as DB column constraints, so both are
 *       accepted rather than guessing which layer fires first.</li>
 * </ul>
 */
@DataJpaTest
@ContextConfiguration(classes = InventoryTestConfig.class)
class ItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemImageRepository itemImageRepository;

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private Item validItem(String sku, String name) {
        Item item = new Item();
        item.setSku(sku);
        item.setName(name);
        item.setCategory("Cement");
        item.setUnitOfMeasure("bag");
        item.setSellingPrice(new BigDecimal("289.50"));
        item.setDefaultCostPrice(new BigDecimal("245.00"));
        item.setActive(true);
        item.setImages(new ArrayList<>());
        return item;
    }

    private ItemImage image(String url, Integer sortOrder) {
        ItemImage image = new ItemImage();
        image.setImageUrl(url);
        image.setSortOrder(sortOrder);
        return image;
    }

    // ---------------------------------------------------------------
    // SKU uniqueness
    // ---------------------------------------------------------------

    @Nested
    class SkuUniquenessConstraint {

        @Test
        void shouldSaveItemWithUniqueSkuSuccessfully() {
            Item item = validItem("SKU-001", "Portland Cement 40kg");

            Item saved = itemRepository.saveAndFlush(item);

            assertThat(saved.getId()).isNotNull();

            entityManager.clear();
            Item reloaded = itemRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getSku()).isEqualTo("SKU-001");
            assertThat(reloaded.getName()).isEqualTo("Portland Cement 40kg");
        }

        @Test
        void shouldThrowDataIntegrityViolationExceptionWhenSavingDuplicateSku() {
            Item first = validItem("SKU-100", "Deformed Rebar 10mm x 6m");
            itemRepository.saveAndFlush(first);
            entityManager.clear();

            Item duplicate = validItem("SKU-100", "A Completely Different Item Name");

            // saveAndFlush forces the insert (and the DB-level unique index
            // check) immediately, rather than deferring to end-of-transaction,
            // so the constraint is verified at the DB level, not just via
            // application code.
            assertThatThrownBy(() -> itemRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ---------------------------------------------------------------
    // NOT NULL constraints
    // ---------------------------------------------------------------

    @Nested
    class NotNullConstraints {

        @Test
        void shouldThrowConstraintViolationWhenSkuIsNull() {
            Item item = validItem(null, "Gravel 3/4 Minus");

            assertThatThrownBy(() -> itemRepository.saveAndFlush(item))
                    .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
        }

        @Test
        void shouldThrowConstraintViolationWhenNameIsNull() {
            Item item = validItem("SKU-200", null);

            assertThatThrownBy(() -> itemRepository.saveAndFlush(item))
                    .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
        }
    }

    // ---------------------------------------------------------------
    // findBySku
    // ---------------------------------------------------------------

    @Nested
    class FindBySkuTests {

        @Test
        void shouldReturnItemWhenSkuExists() {
            Item item = validItem("SKU-300", "16mm Plywood Marine 4x8");
            itemRepository.saveAndFlush(item);
            entityManager.clear();

            Optional<Item> result = itemRepository.findBySku("SKU-300");

            assertThat(result).isPresent();
            assertThat(result.get().getSku()).isEqualTo("SKU-300");
            assertThat(result.get().getName()).isEqualTo("16mm Plywood Marine 4x8");
        }

        @Test
        void shouldReturnEmptyWhenSkuDoesNotExist() {
            Optional<Item> result = itemRepository.findBySku("SKU-DOES-NOT-EXIST");

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // Image cascade + orphanRemoval
    // ---------------------------------------------------------------

    @Nested
    class ImageCascadeAndOrphanRemoval {

        @Test
        void shouldCascadeSaveImagesWithCorrectItemForeignKey() {
            Item item = validItem("SKU-400", "Angle Bar 25mm x 3mm x 6m");
            ItemImage front = image("https://cdn.example.com/angle-bar-front.jpg", 0);
            ItemImage side = image("https://cdn.example.com/angle-bar-side.jpg", 1);
            front.setItem(item);
            side.setItem(item);
            item.getImages().add(front);
            item.getImages().add(side);

            Item saved = itemRepository.saveAndFlush(item);
            Long savedItemId = saved.getId();
            entityManager.clear();

            // Verify via the parent association...
            Item reloaded = itemRepository.findById(savedItemId).orElseThrow();
            assertThat(reloaded.getImages()).hasSize(2);
            assertThat(reloaded.getImages())
                    .extracting(ItemImage::getImageUrl)
                    .containsExactlyInAnyOrder(
                            "https://cdn.example.com/angle-bar-front.jpg",
                            "https://cdn.example.com/angle-bar-side.jpg");

            // ...and independently via ItemImageRepository, to confirm the
            // item_id foreign key was actually persisted on the child rows.
            List<ItemImage> imagesForThisItem = itemImageRepository.findAll().stream()
                    .filter(img -> img.getItem() != null && savedItemId.equals(img.getItem().getId()))
                    .toList();
            assertThat(imagesForThisItem).hasSize(2);
        }

        @Test
        void shouldDeleteAssociatedImagesWhenItemIsDeleted() {
            Item item = validItem("SKU-500", "Hollow Block 4in CHB");
            ItemImage onlyImage = image("https://cdn.example.com/chb-4in.jpg", 0);
            onlyImage.setItem(item);
            item.getImages().add(onlyImage);

            Item saved = itemRepository.saveAndFlush(item);
            Long itemId = saved.getId();
            Long imageId = saved.getImages().get(0).getId();
            entityManager.clear();

            assertThat(itemImageRepository.findById(imageId)).isPresent();

            itemRepository.deleteById(itemId);
            entityManager.flush();
            entityManager.clear();

            assertThat(itemRepository.findById(itemId)).isEmpty();
            // No orphaned ItemImage row left behind after the parent is gone.
            assertThat(itemImageRepository.findById(imageId)).isEmpty();
        }

        @Test
        void shouldDeleteOnlyTheRemovedImageWhenOneImageIsRemovedFromTheList() {
            Item item = validItem("SKU-600", "Tie Wire #16 Roll");
            ItemImage keepImage = image("https://cdn.example.com/tie-wire-keep.jpg", 0);
            ItemImage removeImage = image("https://cdn.example.com/tie-wire-remove.jpg", 1);
            keepImage.setItem(item);
            removeImage.setItem(item);
            item.getImages().add(keepImage);
            item.getImages().add(removeImage);

            Item saved = itemRepository.saveAndFlush(item);
            Long itemId = saved.getId();
            Long keepImageId = saved.getImages().get(0).getId();
            Long removeImageId = saved.getImages().get(1).getId();
            entityManager.clear();

            Item reloaded = itemRepository.findById(itemId).orElseThrow();
            // Mutate the managed collection in place so Hibernate's
            // orphanRemoval diffing (against its load-time snapshot) picks up
            // the removal correctly.
            reloaded.getImages().removeIf(img -> img.getId().equals(removeImageId));
            entityManager.flush();
            entityManager.clear();

            assertThat(itemImageRepository.findById(removeImageId)).isEmpty();
            assertThat(itemImageRepository.findById(keepImageId)).isPresent();

            Item refetched = itemRepository.findById(itemId).orElseThrow();
            assertThat(refetched.getImages()).hasSize(1);
            assertThat(refetched.getImages().get(0).getId()).isEqualTo(keepImageId);
        }
    }

    // ---------------------------------------------------------------
    // ItemImage.imageUrl has no uniqueness constraint
    // ---------------------------------------------------------------

    @Nested
    class ImageUrlUniqueness {

        @Test
        void shouldAllowTwoDifferentItemsToShareTheSameImageUrl() {
            String sharedUrl = "https://cdn.example.com/generic-placeholder.jpg";

            Item itemA = validItem("SKU-700", "Placeholder Item A");
            ItemImage imageA = image(sharedUrl, 0);
            imageA.setItem(itemA);
            itemA.getImages().add(imageA);

            Item itemB = validItem("SKU-701", "Placeholder Item B");
            ItemImage imageB = image(sharedUrl, 0);
            imageB.setItem(itemB);
            itemB.getImages().add(imageB);

            assertThatCode(() -> {
                itemRepository.saveAndFlush(itemA);
                itemRepository.saveAndFlush(itemB);
            }).doesNotThrowAnyException();

            entityManager.clear();

            List<ItemImage> imagesWithSharedUrl = itemImageRepository.findAll().stream()
                    .filter(img -> sharedUrl.equals(img.getImageUrl()))
                    .toList();
            assertThat(imagesWithSharedUrl).hasSize(2);
            assertThat(imagesWithSharedUrl)
                    .extracting(img -> img.getItem().getId())
                    .containsExactlyInAnyOrder(itemA.getId(), itemB.getId());
        }
    }
}