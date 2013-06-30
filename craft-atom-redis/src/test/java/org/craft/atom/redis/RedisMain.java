package org.craft.atom.redis;

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import junit.framework.Assert;

import org.craft.atom.redis.api.Redis;
import org.craft.atom.redis.api.RedisDataException;
import org.craft.atom.redis.api.RedisFactory;
import org.craft.atom.redis.api.RedisTransaction;

/**
 * @author mindwind
 * @version 1.0, Jun 19, 2013
 */
public class RedisMain extends TestMain {
	
	private static final String HOST = "127.0.0.1";
	private static final int PORT = 6379;
	private static final String K = "test-key";
	private static final String V = "test-value";
	private static Redis r;
	
	private static void init() {
		r = RedisFactory.newRedis(HOST, PORT);
	}
	
	protected static void after() {
		r.flushall();
	}
	
	public static void main(String[] args) throws Exception {
		init();
		
		// Keys
		del();
		exists();
		expire();
		expireat();
		keys();
		sort_by_get();
		
		// Hashes
		
		// Lists
		blpop();
		lpush_lrange_llen();
		
		// Sets
		
		// Sorted Sets
		
		// Pub/Sub
		
		// Transactions
		multi_exec();
		watch_multi_exec();
		multi_discard();
		watch_unwatch_multi_exec();
	}
	
	
	// ~ ------------------------------------------------------------------------------------------------ Test Cases
	
	
	private static void blpop() throws InterruptedException {
		before("blpop");
		
		final Lock lock = new ReentrantLock();
		final Condition c = lock.newCondition();
		
		Thread t1 = new Thread(new Runnable() {	
			@Override
			public void run() {
				String v = r.blpop(K);
				Assert.assertEquals("3", v);
				lock.lock();
				try {
					c.signal();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					lock.unlock();
				}
			}
		});
		
		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {
				r.lpush(K, "1", "2", "3");
			}
		});
		
		t1.start();
		Thread.sleep(1000);
		t2.start();
		
		lock.lock();
		try {
			c.await();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
		
		after();
	}
	
	private static void lpush_lrange_llen() {
		before("lpush_lrange_llen");
		
		long len = r.lpush(K, "1", "2", "3");
		Assert.assertEquals(3, len);
		len = r.llen(K);
		Assert.assertEquals(3, len);
		List<String> l = r.lrange(K, 0, -1);
		Assert.assertEquals("1", l.get(2));
		
		after();
	}
	
	private static void sort_by_get() {
		before("testSortByGet");
		
		r.lpush(K, "1", "2", "3");
		r.set("w_1", "3");
		r.set("w_2", "2");
		r.set("w_3", "1");
		r.set("o_1", "1-aaa");
		r.set("o_2", "2-bbb");
		r.set("o_3", "3-ccc");
		List<String> l = r.sort(K, "w_*");
		Assert.assertEquals("1", l.get(2));
		l = r.sort(K, "w_*", new String[] { "o_*" });
		Assert.assertEquals("1-aaa", l.get(2));
		
		after();
	}
	
	private static void keys() {
		before("keys");
		
		r.set(K, V);
		Set<String> keys = r.keys("test*");
		Assert.assertTrue(keys.size() > 0);
		
		after();
	}
	
	private static void expireat() throws InterruptedException {
		before("expireat");
		
		r.set(K, V);
		r.expireat(K, (System.currentTimeMillis() + 2000) / 1000);
		Thread.sleep(3000);
		boolean b = r.exists(K);
		Assert.assertEquals(false, b);
		
		after();
	}
	
	private static void expire() throws InterruptedException {
		before("expire");
		
		r.set(K, V);
		r.expire(K, 3);
		Thread.sleep(4000);
		boolean b = r.exists(K);
		Assert.assertEquals(false, b);
		
		after();
	}
	
	private static void exists() {
		before("exists");
		
		r.set(K, V);
		boolean b = r.exists(K);
		Assert.assertEquals(true, b);
		
		after();
	}
	
	private static void del() {
		before("del");
		
		r.set(K, V);
		r.del(K);
		boolean b = r.exists(K);
		Assert.assertEquals(false, b);
		
		after();
	}
	
	private static void watch_unwatch_multi_exec() {
		before("watch_unwatch_multi_exec");
		
		String wkey = "watch-key";
		r.watch(wkey);
		r.set(wkey, "1");
		r.unwatch();
		RedisTransaction t = r.multi();
		t.set(K, V);
		r.exec(t);
		String v = r.get(K);
		Assert.assertEquals(V, v);
		
		after();
	}
	
	private static void multi_discard() {
		before("multi_discard");
		
		RedisTransaction t = r.multi();
		t.set(K, V);
		t.get(K);
		r.discard(t);
		try {
			r.exec(t);
			Assert.fail();
		} catch (RedisDataException e) {
		}
		
		after();
	}
	
	private static void watch_multi_exec() throws InterruptedException {
		before("watch_multi_exec");
		
		final String wkey = "watch-key";
		final Lock lock = new ReentrantLock();
		final Condition c1 = lock.newCondition();
		final Condition c2 = lock.newCondition();
		Thread t1 = new Thread(new Runnable() {
			@Override
			public void run() {
				lock.lock();
				try {
					r.watch(wkey);
					c1.await();
					RedisTransaction t = r.multi();
					t.set(K, V);
					r.exec(t);
					c2.signal();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					lock.unlock();
				}
			}
		});
		t1.start();
		
		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {
				lock.lock();
				try {
					r.set(wkey, "1");
					c1.signal();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					lock.unlock();
				}
			}
		});
		t2.start();
		
		lock.lock();
		try {
			c2.await();
		} finally {
			lock.unlock();
		}
		
		String v = r.get(K);
		Assert.assertNull(v);
		r.del(wkey);
		
		after();
	}
	
	private static void multi_exec() {
		before("multi_exec");
		
		RedisTransaction t = r.multi();
		t.set(K, V);
		t.get(K);
		List<Object> l = r.exec(t);
		Assert.assertEquals(2, l.size());
		Assert.assertEquals(V, l.get(1));
		
		after();
	}
	
}
