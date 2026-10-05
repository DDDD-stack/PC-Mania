package al.pcmania.repo;

import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionOrderByIdAsc(ChatSession session);

    /** All messages of several sessions in one query, for the transcript list. */
    @Query("select m from ChatMessage m where m.session.id in :ids order by m.session.id, m.id")
    List<ChatMessage> findBySessionIds(@Param("ids") Collection<Long> ids);

    /** The first customer message of each session: the subject line of the transcript list. */
    @Query("select m from ChatMessage m where m.role = :role and m.id in (select min(x.id) from ChatMessage x where x.role = :role and x.session.id in :ids group by x.session.id)")
    List<ChatMessage> firstByRole(@Param("ids") Collection<Long> ids, @Param("role") ChatRole role);
}
