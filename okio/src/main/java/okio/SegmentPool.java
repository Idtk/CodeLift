/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

/**
 * A collection of unused segments, necessary to avoid GC churn and zero-fill.
 * This pool is a thread-safe static singleton.
 */
final class SegmentPool {
  /** The maximum number of bytes to pool. */
  // TODO: Is 64 KiB a good maximum size? Do we ever have that many idle segments?
  static final long MAX_SIZE = 64 * 1024; // 64 KiB.

  /** Singly-linked list of segments. */
  static Segment next;

  /** Total bytes in this pool. */
  static long byteCount;

  private SegmentPool() {
  }

  /**
   * new next = old next.next，方便下次使用；
   * result = next，且 result.next = null，返回 result
   * @return
   */
  static Segment take() {
    synchronized (SegmentPool.class) {// 锁保护
      if (next != null) {// 取出一个Segment，池中的字节数减少
        Segment result = next;
        next = result.next;
        result.next = null;
        byteCount -= Segment.SIZE;// 总字节数中移去一个Segment的大小
        return result;
      }
    }
    // Pool中没有Segment，有可能是新的第一个Segment，也有可能是之前Pool中的Segment都被使用了
    return new Segment(); // Pool is empty. Don't zero-fill while holding a lock.
  }

  static void recycle(Segment segment) {
    if (segment.next != null || segment.prev != null) throw new IllegalArgumentException();
    if (segment.shared) return; // This segment cannot be recycled.
    synchronized (SegmentPool.class) {// 锁保护
      // 如果在限定的池尺寸范围内，则在Pool中加入这个回收的Segment，池的字节数也相应增加
      if (byteCount + Segment.SIZE > MAX_SIZE) return; // Pool is full.
      byteCount += Segment.SIZE;
      segment.next = next;
      segment.pos = segment.limit = 0;// 清空
      next = segment;
    }
  }
}
