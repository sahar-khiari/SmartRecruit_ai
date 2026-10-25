import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PublicNavbar } from '../../shared/components/public-navbar/public-navbar';

@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet, RouterLink, MatIconModule, PublicNavbar],
  templateUrl: './public-layout.html',
  styleUrl: './public-layout.scss',
})
export class PublicLayout {
  protected readonly currentYear = new Date().getFullYear();
}
