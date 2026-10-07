package org.aiknow.server.notice;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {
    private final NoticeRepository notices;
    private final Clock clock;

    public Page<NoticeDtos.View> list(boolean admin, int page, int size) {
        var pageable = PageRequest.of(page, size);
        return (admin ? notices.findAllByOrderByIdDesc(pageable)
            : notices.findByPublishedTrueOrderByPublishedAtDescIdDesc(pageable)).map(NoticeDtos.View::from);
    }

    public NoticeDtos.View get(Long id, boolean admin) {
        var notice = find(id);
        if (!admin && !notice.isPublished()) throw notFound();
        return NoticeDtos.View.from(notice);
    }

    @Transactional
    public NoticeDtos.View create(NoticeDtos.Write request) {
        return NoticeDtos.View.from(notices.saveAndFlush(Notice.create(request, clock.instant())));
    }

    @Transactional
    public NoticeDtos.View update(Long id, NoticeDtos.Write request) {
        var notice = find(id);
        notice.update(request, clock.instant());
        return NoticeDtos.View.from(notices.saveAndFlush(notice));
    }

    @Transactional
    public void delete(Long id) { notices.delete(find(id)); }

    private Notice find(Long id) { return notices.findById(id).orElseThrow(this::notFound); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."); }
}
