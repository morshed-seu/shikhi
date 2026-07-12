package com.shikhi.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;

/**
 * Hand-rolled in-memory {@link JpaRepository} test double (no Mockito on the test classpath —
 * see backend/build.gradle). Backs plain unit tests for services that depend on a repository
 * but don't need a real database (mirrors how {@code PracticeGeneratorTest} avoids a Spring
 * context). Only {@code save}/{@code findById}/{@code deleteById} and friends are exercised in
 * practice; the paging/query-by-example surface is implemented just enough to satisfy the
 * interface and throws if a test ever actually needs it.
 */
public abstract class InMemoryJpaRepository<T, ID> implements JpaRepository<T, ID> {

	protected final Map<ID, T> store = new LinkedHashMap<>();

	protected abstract ID idOf(T entity);

	@Override
	public <S extends T> S save(S entity) {
		store.put(idOf(entity), entity);
		return entity;
	}

	@Override
	public <S extends T> List<S> saveAll(Iterable<S> entities) {
		List<S> saved = new ArrayList<>();
		entities.forEach(e -> saved.add(save(e)));
		return saved;
	}

	@Override
	public Optional<T> findById(ID id) {
		return Optional.ofNullable(store.get(id));
	}

	@Override
	public boolean existsById(ID id) {
		return store.containsKey(id);
	}

	@Override
	public List<T> findAll() {
		return new ArrayList<>(store.values());
	}

	@Override
	public List<T> findAllById(Iterable<ID> ids) {
		List<T> result = new ArrayList<>();
		ids.forEach(id -> {
			T value = store.get(id);
			if (value != null) {
				result.add(value);
			}
		});
		return result;
	}

	@Override
	public long count() {
		return store.size();
	}

	@Override
	public void deleteById(ID id) {
		store.remove(id);
	}

	@Override
	public void delete(T entity) {
		store.remove(idOf(entity));
	}

	@Override
	public void deleteAllById(Iterable<? extends ID> ids) {
		ids.forEach(store::remove);
	}

	@Override
	public void deleteAll(Iterable<? extends T> entities) {
		entities.forEach(this::delete);
	}

	@Override
	public void deleteAll() {
		store.clear();
	}

	@Override
	public List<T> findAll(Sort sort) {
		return findAll();
	}

	@Override
	public Page<T> findAll(Pageable pageable) {
		throw new UnsupportedOperationException("not needed by unit tests using this fake");
	}

	@Override
	public void flush() {
		// no-op: nothing is buffered
	}

	@Override
	public <S extends T> S saveAndFlush(S entity) {
		return save(entity);
	}

	@Override
	public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
		return saveAll(entities);
	}

	@Override
	public void deleteAllInBatch(Iterable<T> entities) {
		deleteAll(entities);
	}

	@Override
	public void deleteAllByIdInBatch(Iterable<ID> ids) {
		deleteAllById(ids);
	}

	@Override
	public void deleteAllInBatch() {
		deleteAll();
	}

	@Override
	@SuppressWarnings("deprecation")
	public T getOne(ID id) {
		return store.get(id);
	}

	@Override
	@SuppressWarnings("deprecation")
	public T getById(ID id) {
		return store.get(id);
	}

	@Override
	public T getReferenceById(ID id) {
		return store.get(id);
	}

	@Override
	public <S extends T> Optional<S> findOne(Example<S> example) {
		throw new UnsupportedOperationException("not needed by unit tests using this fake");
	}

	@Override
	public <S extends T> List<S> findAll(Example<S> example) {
		throw new UnsupportedOperationException("not needed by unit tests using this fake");
	}

	@Override
	public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
		throw new UnsupportedOperationException("not needed by unit tests using this fake");
	}

	@Override
	public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
		throw new UnsupportedOperationException("not needed by unit tests using this fake");
	}

	@Override
	public <S extends T> long count(Example<S> example) {
		throw new UnsupportedOperationException("not needed by unit tests using this fake");
	}

	@Override
	public <S extends T> boolean exists(Example<S> example) {
		throw new UnsupportedOperationException("not needed by unit tests using this fake");
	}

	@Override
	public <S extends T, R> R findBy(Example<S> example,
			Function<FetchableFluentQuery<S>, R> queryFunction) {
		throw new UnsupportedOperationException("not needed by unit tests using this fake");
	}
}
