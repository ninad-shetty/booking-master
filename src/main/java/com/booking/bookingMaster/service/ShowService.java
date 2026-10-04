package com.booking.bookingMaster.service;

import com.booking.bookingMaster.model.Seat;
import com.booking.bookingMaster.model.SeatStatus;
import com.booking.bookingMaster.model.Show;
import com.booking.bookingMaster.controller.Dtos.SeatView;
import com.booking.bookingMaster.controller.Dtos.ShowView;
import com.booking.bookingMaster.dao.SeatDao;
import com.booking.bookingMaster.dao.ShowDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ShowService {

    private final ShowDao showDao;
    private final SeatDao seatDao;

    public ShowService(ShowDao showDao, SeatDao seatDao) {
        this.showDao = showDao;
        this.seatDao = seatDao;
    }

    @Transactional
    public Show createShow(String name, List<String> seatNumbers, int pricePaise, int perUserLimit) {
        Show show = new Show();
        show.setName(name);
        show.setTotalSeats(seatNumbers.size());
        show.setPricePaise(pricePaise);
        show.setPerUserLimit(perUserLimit);

        show = showDao.save(show);

        List<Seat> seats = new ArrayList<>();
        for (String seatNumber : seatNumbers) {
            Seat seat = new Seat();
            seat.setShow(show);
            seat.setSeatNumber(seatNumber);
            seat.setStatus(SeatStatus.AVAILABLE);
            seats.add(seat);
        }
        seatDao.saveAll(seats);

        return show;
    }

    public Show getShow(Long showId) {
        return showDao.findById(showId)
                .orElseThrow(() -> new NoSuchElementException("Show not found with id: " + showId));
    }

    public List<Show> getAllShows() {
        return showDao.findAll();
    }

    @Transactional(readOnly = true)
    public ShowView getShowView(Long showId) {
        Show show = getShow(showId);
        return toShowView(show, seatDao.findByShowId(showId));
    }

    @Transactional(readOnly = true)
    public List<ShowView> getAllShowViews() {
        List<Show> shows = showDao.findAll();
        List<Long> showIds = shows.stream().map(Show::getId).toList();
        Map<Long, List<Seat>> seatsByShow = new HashMap<>();
        for (Seat seat : seatDao.findByShowIds(showIds)) {
            seatsByShow.computeIfAbsent(seat.getShow().getId(), ignored -> new ArrayList<>()).add(seat);
        }

        return shows.stream()
                .map(show -> toShowView(show, seatsByShow.getOrDefault(show.getId(), List.of())))
                .toList();
    }

    private ShowView toShowView(Show show, List<Seat> seats) {
        long available = 0;
        long held = 0;
        long confirmed = 0;
        List<SeatView> seatViews = new ArrayList<>(seats.size());

        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                available++;
            } else if (seat.getStatus() == SeatStatus.HELD) {
                held++;
            } else if (seat.getStatus() == SeatStatus.CONFIRMED) {
                confirmed++;
            }
            seatViews.add(new SeatView(seat.getSeatNumber(), seat.getStatus().name(), seat.getHolderUserId()));
        }

        return new ShowView(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                show.getTotalSeats(),
                available,
                held,
                confirmed,
                seatViews
        );
    }
}