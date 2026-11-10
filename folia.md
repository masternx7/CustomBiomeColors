# ทำ Folia support

Folia ไม่รองรับ `Bukkit.getScheduler()` และ `Bukkit.getServer().getScheduler()` ใช้ [UniversalScheduler](https://github.com/Anon8281/UniversalScheduler) แล้ว shade เข้า jar ของปลั๊กอิน

ไลบรารีเลือก scheduler ให้เอง:

- Folia / Canvas ใช้ region scheduler
- Paper ใช้ Paper scheduler
- Spigot / Bukkit ใช้ Bukkit scheduler

บน Spigot พฤติกรรมยังเป็นเธรดหลักกับเธรด async เหมือนเดิม ต้องใช้ Java 8 ขึ้นไป

แทน `[VERSION]` ด้วยแท็กล่าสุดของ repo เช่น `0.1.7` และแทน `[YOUR_PLUGIN_PACKAGE]` ด้วยแพ็กเกจหลักของปลั๊กอินนั้น

## 1. Maven

เพิ่ม repo ถ้ายังไม่มี

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>
```

```xml
<dependency>
    <groupId>com.github.Anon8281</groupId>
    <artifactId>UniversalScheduler</artifactId>
    <version>[VERSION]</version>
    <scope>compile</scope>
</dependency>
```

Shade แล้ว relocate กันชนกับปลั๊กอินอื่นที่ใส่ไลบรารีตัวเดียวกัน ถ้ามี shade plugin อยู่แล้ว ให้รวม `include` กับ `relocation` เข้าไปใน execution เดิม อย่าสร้าง shade ซ้ำจนทับของเก่า

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <artifactSet>
                    <includes>
                        <include>com.github.Anon8281:UniversalScheduler</include>
                    </includes>
                </artifactSet>
                <relocations>
                    <relocation>
                        <pattern>com.github.Anon8281.universalScheduler</pattern>
                        <shadedPattern>[YOUR_PLUGIN_PACKAGE].universalScheduler</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

โค้ดยัง import `com.github.Anon8281.universalScheduler` ตามปกติ shade จะเปลี่ยนชื่อแพ็กเกจตอน `package`

ถ้า `mvn compile` ฟ้องว่าหาคลาส Paper ไม่เจอ ทั้งที่ปลั๊กอินรันบน Spigot ได้ ให้ใส่ `paper-api` แบบ `provided` เพิ่ม คลาสนี้ใช้ตอนคอมไพล์อย่างเดียว ไม่ถูกแพ็กเข้า jar

โปรเจกต์ Gradle ใช้แบบนี้แทนบล็อก Maven ด้านบน แทน `[VERSION]` ด้วยแท็กล่าสุดเช่น `0.1.7` และแทน `[YOUR_PLUGIN_PACKAGE]` ด้วยแพ็กเกจหลักของปลั๊กอิน

```groovy
repositories {
    //...
    maven { url 'https://jitpack.io' }
}
dependencies {
    //...
    implementation 'com.github.Anon8281:UniversalScheduler:[VERSION]'
}
```

Shade ด้วย Shadow แล้ว relocate เข้า `shadowJar` ที่มีอยู่แล้ว อย่าสร้าง shade ซ้ำจนทับของเก่า

```groovy
shadowJar {
    //Don't forget to replace
    relocate 'com.github.Anon8281.universalScheduler', '[YOUR_PLUGIN_PACKAGE].universalScheduler'
}
```

โค้ดยัง import `com.github.Anon8281.universalScheduler` ตามปกติ Shadow จะเปลี่ยนชื่อแพ็กเกจตอน `shadowJar`

## 2. plugin.yml

Folia จะไม่โหลดปลั๊กอินถ้าไม่มีบรรทัดนี้

```yaml
folia-supported: true
```

## 3. คลาสหลัก

`Main` คือคลาสหลักของปลั๊กอิน สร้าง scheduler ให้เสร็จก่อนงานอื่นใน `onEnable()`

```java
private static TaskScheduler scheduler;

@Override
public void onEnable() {
    scheduler = UniversalScheduler.getScheduler(this);
}

@NotNull
public static TaskScheduler getScheduler() {
    return scheduler;
}
```

ถ้าคลาสหลักมี `onEnable()` อยู่แล้ว ให้ใส่บรรทัด `scheduler = ...` ไว้บนสุด แล้วค่อยทำงานเดิม ตอนปิดปลั๊กอินให้ยกเลิกงานที่ค้าง

```java
@Override
public void onDisable() {
    // ปิด manager เดิมก่อน
    if (scheduler != null) {
        scheduler.cancelTasks();
        scheduler = null;
    }
}
```

ถ้าคลาสหลัก extend `NightPlugin` ชื่อ `getScheduler()` ใช้ไม่ได้ เพราะ `NightCorePlugin` มีเมธอด instance ชื่อนี้อยู่แล้วและคืน `BukkitScheduler` ให้ใช้ `getTaskScheduler()` แทน และต้องเซ็ต scheduler ก่อน `super.onEnable()` เพราะ NightCore เรียก `enable()` จากในนั้น

```java
@Override
public void onEnable() {
    scheduler = UniversalScheduler.getScheduler(this);
    super.onEnable();
}

@NotNull
public static TaskScheduler getTaskScheduler() {
    return scheduler;
}
```

ด้านล่างใช้ `Main.getScheduler()` เป็นชื่อเรียก ถ้าปลั๊กอินนั้นใช้ `getTaskScheduler()` ให้เปลี่ยนชื่อเมธอดตามนั้น

## 4. วิธีเรียก

เวลาเป็น tick ตลอด `20 tick = 1 วินาที`

```java
Main.getScheduler().runTask(() -> {
    // เธรดหลักบน Bukkit / global region บน Folia
});

Main.getScheduler().runTaskLater(() -> {
    Bukkit.broadcastMessage("Wow, it was scheduled");
}, 10L);

MyScheduledTask task = Main.getScheduler().runTaskLater(() -> {
    Bukkit.broadcastMessage("Wow, it was scheduled");
}, 10L);

Main.getScheduler().runTaskTimer(() -> {
    // ทำซ้ำบน global region
}, 0L, 20L);

Main.getScheduler().runTaskAsynchronously(() -> {
    // งานฐานข้อมูล หรือเขียนไฟล์ ห้ามอ่านโลกหรือผู้เล่นในนี้
});

Main.getScheduler().runTaskLaterAsynchronously(() -> {
    // async หลัง delay
}, 20L);

Main.getScheduler().runTaskTimerAsynchronously(() -> {
    // async ซ้ำ
}, 0L, 20L);
```

งานที่แตะผู้เล่น inventory หรือ entity ต้องผูกกับ entity นั้น

```java
Main.getScheduler().runTask(player, () -> {
    player.getInventory().addItem(item);
});

Main.getScheduler().runTaskLater(player, () -> {
    player.closeInventory();
}, 1L);

Main.getScheduler().runTaskTimer(player, () -> {
    // ติ๊กอนิเมชันของผู้เล่นคนนี้
}, 0L, 1L);
```

งานที่แตะบล็อก chunk หรือโลก ต้องผูกกับตำแหน่งนั้น

```java
Main.getScheduler().runTask(location, () -> {
    block.getWorld().spawnParticle(particle, location, 1);
});

Main.getScheduler().runTaskLater(location, () -> {
    // แก้บล็อกหลัง delay
}, 1L);

Main.getScheduler().runTaskTimer(location, () -> {
    // เอฟเฟกต์ที่บล็อกนี้
}, 0L, 1L);
```

บน Bukkit `runTask(Location)` และ `runTask(Entity)` ทำงานบนเธรดหลักเหมือน `runTask` ธรรมดา

เก็บ `MyScheduledTask` ของงานที่วนซ้ำ แล้ว `task.cancel()` ตอน manager หรือปลั๊กอินปิด งาน one-shot ไม่ต้องเก็บ `cancelTasks()` ตอน `onDisable()` เก็บกวาดให้

ช่วงเวลา `0` หรือติดลบแปลว่าไม่ต้องตั้ง task นั้น

## 5. ย้ายของเดิม

หาจุดพวกนี้แล้วเปลี่ยนให้หมด อย่าเหลือ `Bukkit.getScheduler()` หรือ `getServer().getScheduler()` ในปลั๊กอินตัวเอง

| ของเดิม | ของใหม่ |
| --- | --- |
| `runTask(plugin, runnable)` | `runTask(runnable)` |
| `runTaskLater(plugin, runnable, delay)` | `runTaskLater(runnable, delay)` |
| `runTaskTimer(plugin, runnable, delay, period)` | `runTaskTimer(runnable, delay, period)` |
| `runTaskAsynchronously(...)` | `runTaskAsynchronously(...)` |
| `runTaskLaterAsynchronously(...)` | `runTaskLaterAsynchronously(...)` |
| `runTaskTimerAsynchronously(...)` | `runTaskTimerAsynchronously(...)` |
| `scheduleSyncDelayedTask` | `runTaskLater` |
| `scheduleSyncRepeatingTask` | `runTaskTimer` |
| `BukkitRunnable#runTaskTimer` | `runTaskTimer` แล้วเก็บ `MyScheduledTask` |

ถ้าปลั๊กอินใช้ NightCore 2.10 เมธอดพวก `plugin.runTask` `addTask` `addAsyncTask` ยังไปที่ Bukkit ต้องย้ายงานของปลั๊กอินออกมาเอง หน่วยเวลาของ NightCore มีสองแบบ

| การเรียกเดิม | ความหมาย |
| --- | --- |
| `addTask(runnable, 1L)` หรือ `addAsyncTask(runnable, 1L)` | `long` = tick ส่ง period นั้นตรง ๆ |
| `addTask(runnable, seconds)` หรือ `addAsyncTask(runnable, seconds)` ที่เป็น `int` | วินาที ต้องคูณ 20 ก่อน |
| `plugin.runTask(task -> ...)` | `runTask(...)` |
| `plugin.runTaskAsync(task -> ...)` | `runTaskAsynchronously(...)` |

```java
public static long secondsToTicks(int seconds) {
    return seconds * 20L;
}
```

`addTask` ของ NightCore ยกเลิก task ให้ตอน manager ปิด ถ้าย้ายออกมาต้อง `cancel()` เองใน `onShutdown()` ก่อนเซฟรอบสุดท้าย

งานภายใน NightCore เช่น `runNextTick` ของเมนู ยังใช้ Bukkit scheduler แก้จากปลั๊กอินลูกไม่ได้ เซิร์ฟเวอร์ Folia ต้องใช้ NightCore ที่รองรับ Folia ด้วย อย่าขยับเวอร์ชัน NightCore อย่างเดียวเพื่อให้ได้ Folia เพราะ API เปลี่ยนและโปรเจกต์เดิมอาจคอมไพล์ไม่ผ่าน

## 6. เธรดบน Folia

เธรด global และเธรด async อ่าน chunk บล็อก หรือตำแหน่งผู้เล่นไม่ได้

- งานผู้เล่นหรือ inventory ใช้ `runTask(player, ...)` หรือ `runTaskTimer(player, ...)`
- งานบล็อกหรือโลกใช้ `runTask(location, ...)` ตำแหน่งต้องมี world ไม่เป็น null
- ก่อน `player.getLocation()` หรือ `player.getWorld()` บน Folia ให้เช็ค `isEntityThread(player)` ถ้าเป็นคนละ region ให้ข้ามหรือส่งงานไปเธรดของผู้เล่นคนนั้น
- `isRegionThread(location)` ใช้เช็คว่ากำลังอยู่เธรดของจุดนั้นอยู่แล้วหรือยัง ถ้าอยู่แล้วให้ทำงานทันที ถ้ายังให้ `runTask(location, ...)`
- `UniversalScheduler.isFolia` กับ `UniversalScheduler.isCanvas` ใช้แยกทางที่ต้องระวัง region บน Bukkit ให้โค้ดเดิมทำงานต่อไปได้

`player.getLocation()` จาก region ของบล็อกใช้ได้เฉพาะผู้เล่นที่ region นั้นเป็นเจ้าของ ผู้เล่นคนละ region ต้องไม่ไปอ่านตำแหน่งจากเธรดของบล็อก
