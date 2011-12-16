#!/usr/bin/env ruby
require 'rubygems'
require 'net/ssh'

filename = ARGV[0]
protocol = ARGV[1]
iout = IO.popen("cat nodelist").readlines
httprepo = iout[0].slice(/[^\n]*/)
putmachine = iout[1].slice(/[^\n]*/)
getmachines = iout[2 .. iout.length-1]
puts "repo #{httprepo}"
puts "putmachine #{putmachine}"

  %x(taktuk -d-1 -f nodelist broadcast exec { 'killall java' })
  %x(taktuk -d-1 -f nodelist broadcast exec { 'killall bttrack' })
  %x(taktuk -d-1 -f nodelist broadcast exec { 'killall /usr/bin/python' })
  out  = %x(taktuk -d-1 -f nodelist broadcast exec { 'killall btpd' })
  puts "kill btpd #{out}" 
  
  
  %x(taktuk -d-1 -f nodelist broadcast exec { 'rm -rf *' })
  %x(taktuk -d-1 -f nodelist broadcast exec { 'rm -rf * .btpd/torrents' })
  IO.popen("scp bitdew-stand-alone-0.2.7.jar "+ httprepo+":") do |f|
    f.readlines
  end
  
    IO.popen("scp lola.avi " + putmachine+":") do |f|
      f.readlines
    end
 
  
  IO.popen("taktuk -d-1 -f nodelist broadcast put { /home/jsaray/sbam_standalone.jar } { /home/jsaray/sbam_standalone.jar }") do |f|
	  f.readlines
  end
  
  IO.popen("taktuk -d-1 -f nodelist broadcast put { /home/jsaray/bitdew-stand-alone-0.2.7.jar } { /home/jsaray/bitdew-stand-alone-0.2.7.jar }") do |f|
	  f.readlines
  end
  
  Net::SSH.start(httprepo,"jsaray")do|ssh|
    ssh.exec "nohup bttrack.bittorrent --port 6969 --dfile dfile > /home/jsaray/trackerout 2> /home/jsaray/trackererr &"
    ssh.exec "nohup java -jar bitdew-stand-alone-0.2.7.jar -v serv dc dt dr ds > initout 2> initerr &"
  end
  puts "sleeping ; wait "
  sleep(20)
  uid = ""
  Net::SSH.start(putmachine,"jsaray")do |ssh|
	  puts "executing put "
	  uid = ssh.exec! "nohup java -jar bitdew-stand-alone-0.2.7.jar put --host "+httprepo+" --protocol bittorrent lola.avi > putout 2> puterr"
	  puts "retrieving output to parse uid"
	  IO.popen("scp " + putmachine +":putout .") do|f|
	    f.readlines
	  end
	  puts "parsing uid"
	  uid = IO.readlines("putout")[1]
	  puts "The id line is #{uid}"
	  uid = uid.match(/[[a-fA-F0-9-]*]/)[0]
	  uid = uid[1,uid.length-2]
	  puts "The uid is  #{uid}"
  end


  puts "begin to execute in a lot of machines"
  
  md5original = IO.popen("md5sum " + filename) do |f|
   f.readlines
  end
  
  getmachines.each{|machine|
    machine = machine.slice(/[^\n]*/)
    Net::SSH.start(machine,"jsaray") do |ssh|
      
      cmd = "nohup java -jar bitdew-stand-alone-0.2.7.jar get --protocol bittorrent --host "+ httprepo +" "+ uid + " > getout 2> geterr &"
      puts "launching on machine #{machine}, command " + cmd
	  ssh.exec cmd
	  puts "done"
    end
  }
  
  puts "this is a new version !!!!!!"
  
  timeini = Time.new          
  md5original = md5original[0].split("\s")[0]
  successclients = 0
  array = Array.new
  getmachines.each{|elem|
    elem = elem.slice(/[^\n]*/)
    a = Thread.new {
      Thread.current[:output] = false
      finish = false
      begin
        Net::SSH.start(elem,"jsaray") do |ssh|
          while !finish do
            if !(protocol.eql? "transmission") 
              result = ssh.exec! "md5sum " + uid
             
            else
              result = ssh.exec! "md5sum Downloads/" + filename
            end
            if protocol.eql? "utorrent"
              running = ssh.exec! "ps -fea | grep utserver"
              lolo = running.split("\n")
              Thread.current[:running] = lolo.length.to_s
              if lolo.length < 3
                Thread.current[:entro] =" ENTROOOOO"
                ssh.exec "nohup ./utserver -configfile conf/settings.conf -daemon > utserverout 2> utservererr &"
              end                       
            end
            Thread.current[:machine] = elem
            result = result.split("\s")[0]
            
            Thread.current[:result] = result
            
            if result.eql? md5original then
              finish = true
              Thread.current[:output] = true
              
            end
          end
        end
      rescue => e
        Thread.current[:output] = true
        puts "Cannot create md5 thread in #{elem}"
        puts e.inspect
	puts e.backtrace
      end
    }
    array << a
  }
  totally = 0
  while !(array.all? {|el| el[:output] }) do
    totally = array.find_all{ |el| el[:output] == true }.length
    array.each{|el|
      puts "md5 in #{el[:machine]} is #{el[:result]} " 
      
    }
    puts "Number of machines " + totally.to_s
    puts "\n\n\n"
    sleep(1)
    totally = array.find_all{ |el| el[:output] == true }.length
    puts "Number of machines " + totally.to_s
  end
  timefi = Time.new
  total = (timefi - timeini).to_s
  puts "Total elapsed time : #{total}" 