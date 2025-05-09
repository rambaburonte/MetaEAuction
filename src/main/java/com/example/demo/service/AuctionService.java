package com.example.demo.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.AuctionRepository;
import com.example.demo.Repository.BidRepository;
import com.example.demo.entity.Auction;
import com.example.demo.entity.Bid;
import com.example.demo.entity.Users;
import com.example.demo.statusEnum.AuctionStatus;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuctionService {
	
	@Autowired
	private AuctionRepository auctionRepository;
	@Autowired
	private BidRepository bidRepository;
	
	@Autowired
	private BidService bidService;

	LocalDateTime NOW =ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
	public List<Auction> getAllAuctions() {
		List<Auction> auctions = auctionRepository.findAll();
		return auctions;
	}

	public List<Auction> getUpcomingAuctions() {
		LocalDateTime NOW = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
		List<Auction> auctions = auctionRepository.findAll();
		List<Auction> upcomingAuctions = auctions.stream()
				.filter(auction -> auction.getStartDate().isAfter(NOW))
				.toList();
		
		return upcomingAuctions;
	}

	public Auction getAuctionById(Integer auctionId) {
		
		Auction auction = auctionRepository.findById(auctionId).orElse(null);
		if (auction != null) {
			return auction;
		}
		
		
		return null;
	}

	public List<Auction> getAuctionByUserId(Users user) {
		// find all actions bided by a user
		 List<Bid> bids = bidRepository.findAllByUser(user);
		 List<Auction> auctions = bids.stream()
					.map(Bid::getAuction)
					.toList();
		 return auctions;
		
	}

	public Auction saveAuction(Auction auction) {
		if (auction != null) {
			return auctionRepository.save(auction);
		}
		
		return null;
	}

	public void deleteAuction(Integer id) {
		if (id != null) {
			auctionRepository.deleteById(id);
		}
		
		
	}

	public List<Auction> getWonAuctionByUserId(Users user) {
		// find all actions won by a user from acution table
		List<Auction> auctions = auctionRepository.findAuctionsWonByUser(user.getId());
		return auctions;
	}
	// based on start and end date time update auction status
	public void  updateAuctionStatus() {
		LocalDateTime NOW = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
		List<Auction> auctions = auctionRepository.findAll();
		bidService.updateBidStatusAndTime();
		// dont update auction status if statuc is CANCELED AND COMPLETED
		for (Auction auction : auctions) {
			if (auction.getStatus() != AuctionStatus.CANCELED && auction.getStatus() != AuctionStatus.COMPLETED) {
				
				if (auction.getStartDate().isBefore(NOW)
						&& auction.getEndDate().isAfter(NOW) && auction.getStatus() != AuctionStatus.ACTIVE) {
					auction.setStatus(AuctionStatus.ACTIVE);
					auctionRepository.save(auction);
				} else if (auction.getEndDate().isBefore(NOW) && auction.getStatus() != AuctionStatus.COMPLETED)  {
					auction.setStatus(AuctionStatus.COMPLETED);
					auctionRepository.save(auction);
				} else if (auction.getStartDate().isAfter(NOW) && auction.getStatus() != AuctionStatus.UPCOMING) {
					auction.setStatus(AuctionStatus.UPCOMING);
					auctionRepository.save(auction);
				}
				
			}
		
		}	
	}
	// update auction end time  plus mins
	public Auction updateAuctionEndTime(Integer auctionId, int minutesToAdd) {
		log.info("actuion  " + auctionId +  " " + minutesToAdd);
		    if (auctionId != null) {
		        Auction auction = auctionRepository.findById(auctionId).orElse(null);
		        
		        if (auction != null) {
		        	
		            auction.setEndDate(auction.getEndDate().plusMinutes(minutesToAdd));
		            log.info( " "  + auction);
		            return auctionRepository.save(auction);
		        }
		    }
		   log.info("auction not updated");
		    return null;
		
	}
	
	

	
}
 