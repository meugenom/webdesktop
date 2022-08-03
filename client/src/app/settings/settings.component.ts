import { Component, OnInit } from '@angular/core';
import { ModalService } from '../modal/modal.service';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.sass']
})
export class SettingsComponent implements OnInit {

  constructor(private modalService: ModalService) { }

  ngOnInit(): void {
  }

  //modals
	openModal(id: string) {
		this.modalService.open(id);
	}

	closeModal(id: string) {
		this.modalService.close(id);
	}

	minimizeModal(id : string){
		this.modalService.minimize(id);
	}

	maximizeModal(id : string){
		this.modalService.maximize(id);
	}

}
