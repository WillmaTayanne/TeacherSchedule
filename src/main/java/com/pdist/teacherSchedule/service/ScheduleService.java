package com.pdist.teacherSchedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pdist.teacherSchedule.amqp.QueueSender;
import com.pdist.teacherSchedule.dto.ScheduleRequest;
import com.pdist.teacherSchedule.model.Message;
import com.pdist.teacherSchedule.model.Schedule;
import com.pdist.teacherSchedule.model.Usuario;
import com.pdist.teacherSchedule.repository.ScheduleRepository;
import com.pdist.teacherSchedule.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private QueueSender queueSender;

    public List<Schedule> read() {
        return this.scheduleRepository.findAll();
    }

    public Schedule readById(Long id){
        return this.scheduleRepository.findById(id).orElse(null);
    }

    public List<Schedule> readForUser(Long idUser) {
        return this.read().stream().filter(schedule -> schedule.getTeacher().getId().equals(idUser) ||
                schedule.getStudents().stream().filter(s -> s.getId().equals(idUser)).toList().size() > 0).toList();
    }

    @Transactional
    public Schedule create(ScheduleRequest scheduleRequest){
        Schedule schedule = scheduleRequest.toSchedule(usuarioRepository);
        return this.scheduleRepository.save(schedule);
    }

    @Transactional
    public Schedule update(Schedule schedule) {
        Schedule scheduleSave = this.scheduleRepository.save(schedule);

        for(Usuario student : scheduleSave.getStudents())
            this.sendMessage(scheduleSave, student, "Notificação de Horário");

        return scheduleSave;
    }

    public void sendMessage(Schedule schedule, Usuario student, String title) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Message message = new Message();

        Timestamp timeBegin = new Timestamp(schedule.getDateTimeBegin().getTime() - 10800000);

        message.setRead(false);
        message.setTitle(title);
        message.setUserIdOrigin(schedule.getTeacher());
        message.setUserIdDestination(student);
        message.setDateTime(new Timestamp(System.currentTimeMillis()));
        message.setDescription("Gostariamos de notificar que sobre a aula em " +
                dateFormat.format(timeBegin));

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        String messageValue = "";
        try {
            messageValue = objectMapper.writeValueAsString(message);
        } catch(Exception e) {}

        System.out.println("[.] Enviando mensagem para ser salva");
        queueSender.send(messageValue);
    }

    public void delete(Long id){
        this.scheduleRepository.deleteById(id);
    }
}
