import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { ConversationResponse } from '../../../core/models/hr/conversation.model';

@Component({
  selector: 'app-conversation-item',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './conversation-item.html',
  styleUrl: './conversation-item.scss',
})
export class ConversationItem {
  readonly conversation = input.required<ConversationResponse>();
  readonly active = input(false);

  readonly select = output<number>();
  readonly rename = output<ConversationResponse>();
  readonly remove = output<ConversationResponse>();
}
