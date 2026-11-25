package com.codemastery.modules.assessment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.TimestampWithTimeZoneJdbcType;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

@Entity
@Table(name = "assignments")
public class AssignmentEntity{

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID course_id;

    @Column(name = "lesson_id")
    private UUID lesson_id;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "instructions", nullable = false, columnDefinition = "text")
    private String instructions;

    @Column(name = "deadline_at", nullable = false)
    @JdbcType(DeadlineJdbcType.class)
    private Instant deadline_at;

    @Column(name = "publication_status", nullable = false, columnDefinition = "text")
    private String publication_status;

    public AssignmentEntity(){
    }

    public AssignmentEntity(
        UUID id,
        UUID course_id,
        UUID lesson_id,
        String title,
        String instructions,
        Instant deadline_at,
        String publication_status
    ) {
        this.id = id;
        this.course_id = course_id;
        this.lesson_id = lesson_id;
        this.title = title;
        this.instructions = instructions;
        this.deadline_at = deadline_at;
        this.publication_status = publication_status;
    }

    public UUID getId(){
        return id;
    }

    public void setId(UUID id){
        this.id = id;
    }

    public UUID getCourseId(){
        return course_id;
    }

    public void setCourseId(UUID course_id){
        this.course_id = course_id;
    }

    public UUID getLessonId(){
        return lesson_id;
    }

    public void setLessonId(UUID lesson_id){
        this.lesson_id = lesson_id;
    }

    public String getTitle(){
        return title;
    }

    public void setTitle(String title){
        this.title = title;
    }

    public String getInstructions(){
        return instructions;
    }

    public void setInstructions(String instructions){
        this.instructions = instructions;
    }

    public Instant getDeadlineAt(){
        return deadline_at;
    }

    public void setDeadlineAt(Instant deadline_at){
        this.deadline_at = deadline_at;
    }

    public String getPublicationStatus(){
        return publication_status;
    }

    public void setPublicationStatus(String publication_status){
        this.publication_status = publication_status;
    }

    /**
     * Assignment-local timestamptz mapping (D2). The inherited JDBC 4.2 OffsetDateTime binder
     * preserves ISO instants on INSERT and targeted JPQL UPDATE. Extract the driver's ISO text
     * without java.sql.Timestamp's hybrid calendar or its OffsetDateTime reader's BC leap-day bug.
     * PostgreSQL 0001 BC is ISO year zero; apply that conversion before constructing the date.
     */
    public static class DeadlineJdbcType extends TimestampWithTimeZoneJdbcType {
        private static final DateTimeFormatter SQL_TIME = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_TIME).appendOffset("+HH:mm:ss", "Z").toFormatter(Locale.ROOT);

        private static Instant instant(String text) {
            if (text == null) return null;
            boolean bc = text.endsWith(" BC");
            String value = bc ? text.substring(0, text.length() - 3) : text;
            int separator = value.indexOf(' ');
            String[] date = value.substring(0, separator).split("-");
            int year = Integer.parseInt(date[0]);
            LocalDate day = LocalDate.of(bc ? 1 - year : year, Integer.parseInt(date[1]), Integer.parseInt(date[2]));
            // PostgreSQL offsets can include historical seconds and omit zero minutes (e.g. +07:06:30, +00).
            return day.atTime(OffsetTime.parse(value.substring(separator + 1), SQL_TIME)).toInstant();
        }

        @Override
        public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
            return new BasicExtractor<>(javaType, this) {
                @Override
                protected X doExtract(ResultSet result, int index, WrapperOptions options) throws SQLException {
                    return javaType.wrap(instant(result.getString(index)), options);
                }

                @Override
                protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                    return javaType.wrap(instant(statement.getString(index)), options);
                }

                @Override
                protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                    return javaType.wrap(instant(statement.getString(name)), options);
                }
            };
        }
    }
}
