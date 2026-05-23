package com.saltlux.nac.record;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecordService {

    private final RecordRepository recordRepository;

    public RecordService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public List<RecordResponse> findAll() {
        return recordRepository.findAll()
                .stream()
                .map(RecordResponse::from)
                .toList();
    }

    public RecordResponse findById(Long id) {
        return RecordResponse.from(getRecord(id));
    }

    @Transactional
    public RecordResponse create(RecordRequest request) {
        Record record = new Record(request.title(), request.description());
        return RecordResponse.from(recordRepository.save(record));
    }

    @Transactional
    public RecordResponse update(Long id, RecordRequest request) {
        Record record = getRecord(id);
        record.update(request.title(), request.description());
        return RecordResponse.from(record);
    }

    @Transactional
    public void delete(Long id) {
        Record record = getRecord(id);
        recordRepository.delete(record);
    }

    private Record getRecord(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Record not found. id=" + id));
    }
}
