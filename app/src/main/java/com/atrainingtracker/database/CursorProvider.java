package com.atrainingtracker.database;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an API method intentionally produces and returns an open SQLite {@link android.database.Cursor}.
 *
 * <p><b>Transfer of Ownership Contract:</b>
 * Any method annotated with {@code @CursorProvider} transfers the responsibility of closing the {@code Cursor}
 * directly to the caller. Callers consuming this method MUST close the cursor deterministically via
 * {@code try-with-resources} or pass ownership to an Android framework lifecycle component (such as
 * {@link android.widget.CursorAdapter} or {@link androidx.loader.content.CursorLoader}).
 * Fields, constructors, and intermediate helpers are strictly prohibited from holding untracked cursor references.</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface CursorProvider {
    /**
     * Optional contract description or caller expectations.
     */
    String value() default "Transfers Cursor ownership and closure responsibility to the caller";
}
